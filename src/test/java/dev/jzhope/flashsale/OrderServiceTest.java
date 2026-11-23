package dev.jzhope.flashsale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import dev.jzhope.flashsale.api.ApiException;
import dev.jzhope.flashsale.event.EventStore;
import dev.jzhope.flashsale.order.OrderService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTest {
  @Autowired OrderService orders;
  @Autowired JdbcTemplate db;
  @MockitoBean Clock clock;
  @MockitoSpyBean EventStore events;
  static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    for (String table :
        List.of(
            "notifications",
            "outbox_events",
            "sale_orders",
            "auth_tokens",
            "app_users",
            "products")) {
      db.update("DELETE FROM " + table);
    }
    db.update("INSERT INTO products(id,name,price_cents,stock) VALUES (1,'Keyboard',19900,20)");
    for (int i = 0; i < 60; i++) {
      db.update(
          "INSERT INTO app_users(id,username,password_hash,created_at) VALUES (?,?,?,?)",
          "u" + i,
          "user" + i,
          "unused",
          Timestamp.from(NOW));
    }
  }

  @Test
  void concurrentBuyersCannotOversell() throws Exception {
    List<Callable<Boolean>> tasks = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      String user = "u" + i;
      tasks.add(
          () -> {
            try {
              orders.create(user, "buy", 1, 1);
              return true;
            } catch (ApiException ex) {
              assertThat(ex.getMessage()).isEqualTo("Sold out");
              return false;
            }
          });
    }
    var pool = Executors.newFixedThreadPool(16);
    try {
      int successes = 0;
      for (var result : pool.invokeAll(tasks)) {
        if (result.get()) {
          successes++;
        }
      }
      assertThat(successes).isEqualTo(20);
    } finally {
      pool.shutdownNow();
    }
    assertThat(stock()).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM sale_orders", Integer.class)).isEqualTo(20);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class))
        .isEqualTo(20);
  }

  @Test
  void simultaneousRetriesReturnOneOrder() throws Exception {
    var pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<String>> tasks = new ArrayList<>();
      for (int i = 0; i < 16; i++) {
        tasks.add(() -> orders.create("u0", "same-key", 1, 2).id());
      }
      var ids = new java.util.HashSet<String>();
      for (var result : pool.invokeAll(tasks)) {
        ids.add(result.get());
      }
      assertThat(ids).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
    assertThat(stock()).isEqualTo(18);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isEqualTo(1);
  }

  @Test
  void rejectsReusingKeyForDifferentRequest() {
    orders.create("u0", "key", 1, 1);
    assertThatThrownBy(() -> orders.create("u0", "key", 1, 2))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("different request");
    assertThat(stock()).isEqualTo(19);
  }

  @Test
  void cancellationRestoresStockExactlyOnce() {
    var order = orders.create("u0", "key", 1, 3);
    orders.transition("u0", order.id(), "cancel");
    orders.transition("u0", order.id(), "cancel");
    assertThat(stock()).isEqualTo(20);
    assertThatThrownBy(() -> orders.transition("u0", order.id(), "pay"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void paidOrdersCannotBeCancelled() {
    var order = orders.create("u0", "key", 1, 1);
    assertThat(orders.transition("u0", order.id(), "pay").status()).isEqualTo("PAID");
    orders.transition("u0", order.id(), "pay");
    assertThatThrownBy(() -> orders.transition("u0", order.id(), "cancel"))
        .isInstanceOf(ApiException.class);
    assertThat(stock()).isEqualTo(19);
  }

  @Test
  void latePaymentExpiresOrderAndRestoresStock() {
    var order = orders.create("u0", "key", 1, 2);
    when(clock.instant()).thenReturn(NOW.plusSeconds(901));
    assertThat(orders.transition("u0", order.id(), "pay").status()).isEqualTo("EXPIRED");
    orders.transition("u0", order.id(), "expire");
    assertThat(stock()).isEqualTo(20);
  }

  @Test
  void anotherUserCannotReadOrChangeOrder() {
    var order = orders.create("u0", "key", 1, 1);
    assertThatThrownBy(() -> orders.get("u1", order.id())).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> orders.transition("u1", order.id(), "pay"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void failedOutboxWriteRollsBackInventoryAndOrder() {
    EventStore target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(events);
    doThrow(new IllegalStateException("injected outbox failure"))
        .when(target)
        .append(anyString(), anyString(), anyString());
    assertThatThrownBy(() -> orders.create("u0", "key", 1, 1))
        .hasMessageContaining("outbox failure");
    assertThat(stock()).isEqualTo(20);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM sale_orders", Integer.class)).isZero();
  }

  private int stock() {
    return db.queryForObject("SELECT stock FROM products WHERE id=1", Integer.class);
  }
}

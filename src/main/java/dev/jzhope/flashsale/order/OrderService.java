package dev.jzhope.flashsale.order;

import dev.jzhope.flashsale.api.ApiException;
import dev.jzhope.flashsale.event.EventStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在 SQL 事务中维护库存、幂等订单、状态转换以及对应的 Outbox 事件。 */
@Service
public class OrderService {
  private final JdbcTemplate db;
  private final EventStore events;
  private final Clock clock;
  private final long ttl;

  public OrderService(
      JdbcTemplate db,
      EventStore events,
      Clock clock,
      @Value("${app.order-ttl-seconds}") long ttl) {
    this.db = db;
    this.events = events;
    this.clock = clock;
    this.ttl = ttl;
  }

  /** 订单快照；金额以整数分表示，时间为 UTC 时间线上的 Instant。 */
  public record Order(
      String id,
      String userId,
      long productId,
      int quantity,
      long totalCents,
      String status,
      Instant createdAt,
      Instant expiresAt) {}

  private static Order map(ResultSet rs, int row) throws SQLException {
    return new Order(
        rs.getString("id"),
        rs.getString("user_id"),
        rs.getLong("product_id"),
        rs.getInt("quantity"),
        rs.getLong("total_cents"),
        rs.getString("status"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("expires_at").toInstant());
  }

  /** 创建订单时，库存扣减、订单插入和事件写入必须一起提交或一起回滚。 */
  @Transactional
  public Order create(String userId, String key, long productId, int quantity) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{1,64}") || quantity < 1 || quantity > 5) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid idempotency key or quantity");
    }
    // 先锁用户行，使同用户的并发重试在查询幂等记录前排队；不同用户仍可并发。
    db.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE", String.class, userId);
    var previous =
        db.query(
            "SELECT * FROM sale_orders WHERE user_id=? AND idempotency_key=?",
            OrderService::map,
            userId,
            key);
    if (!previous.isEmpty()) {
      Order existing = previous.get(0);
      if (existing.productId() != productId || existing.quantity() != quantity) {
        throw new ApiException(
            HttpStatus.CONFLICT, "Idempotency key already used for a different request");
      }
      return existing;
    }
    var prices =
        db.queryForList("SELECT price_cents FROM products WHERE id=?", Long.class, productId);
    if (prices.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Product not found");
    }
    // 条件更新由数据库原子执行，多个应用副本竞争时也不会把库存扣成负数。
    if (db.update(
            "UPDATE products SET stock=stock-? WHERE id=? AND stock>=?",
            quantity,
            productId,
            quantity)
        == 0) {
      throw new ApiException(HttpStatus.CONFLICT, "Sold out");
    }
    Instant now = clock.instant();
    Order order =
        new Order(
            UUID.randomUUID().toString(),
            userId,
            productId,
            quantity,
            Math.multiplyExact(prices.get(0), quantity),
            "PENDING",
            now,
            now.plusSeconds(ttl));
    db.update(
        "INSERT INTO sale_orders(id,user_id,product_id,quantity,total_cents,status,idempotency_key,created_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?)",
        order.id(),
        userId,
        productId,
        quantity,
        order.totalCents(),
        order.status(),
        key,
        Timestamp.from(now),
        Timestamp.from(order.expiresAt()));
    // 不在库存事务中等待 Kafka；同事务写入 Outbox，由发布器异步投递。
    events.append("ORDER_CREATED", order.id(), userId);
    return order;
  }

  public Order get(String userId, String id) {
    var rows =
        db.query(
            "SELECT * FROM sale_orders WHERE id=? AND user_id=?", OrderService::map, id, userId);
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Order not found");
    }
    return rows.get(0);
  }

  public List<Order> list(String userId) {
    return db.query(
        "SELECT * FROM sale_orders WHERE user_id=? ORDER BY created_at DESC LIMIT 100",
        OrderService::map,
        userId);
  }

  /** 锁定订单后重新判断状态，防止支付、取消和过期任务重复释放库存。 */
  @Transactional
  public Order transition(String userId, String id, String action) {
    var rows =
        db.query(
            "SELECT * FROM sale_orders WHERE id=? AND user_id=? FOR UPDATE",
            OrderService::map,
            id,
            userId);
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Order not found");
    }
    Order old = rows.get(0);
    String next =
        switch (action) {
          case "pay" -> "PAID";
          case "cancel" -> "CANCELLED";
          case "expire" -> "EXPIRED";
          default -> throw new IllegalArgumentException("Unknown order action");
        };
    if (!old.status().equals("PENDING")) {
      if (old.status().equals(next) || action.equals("expire")) {
        return old;
      }
      throw new ApiException(HttpStatus.CONFLICT, "Order is already " + old.status());
    }
    // 支付接口也检查到期时间，避免定时扫描尚未执行时接受超期付款。
    boolean expired = !old.expiresAt().isAfter(clock.instant());
    if (action.equals("expire") && !expired) {
      return old;
    }
    if (expired) {
      next = "EXPIRED";
    }
    db.update("UPDATE sale_orders SET status=? WHERE id=?", next, id);
    if (!next.equals("PAID")) {
      db.update("UPDATE products SET stock=stock+? WHERE id=?", old.quantity(), old.productId());
    }
    events.append("ORDER_" + next, id, userId);
    return new Order(
        old.id(),
        old.userId(),
        old.productId(),
        old.quantity(),
        old.totalCents(),
        next,
        old.createdAt(),
        old.expiresAt());
  }

  public List<Order> expiredCandidates() {
    return db.query(
        "SELECT * FROM sale_orders WHERE status='PENDING' AND expires_at<=? ORDER BY expires_at LIMIT 100",
        OrderService::map,
        Timestamp.from(clock.instant()));
  }
}

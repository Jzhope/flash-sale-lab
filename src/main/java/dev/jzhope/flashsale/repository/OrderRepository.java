package dev.jzhope.flashsale.repository;

import dev.jzhope.flashsale.model.Order;
import dev.jzhope.flashsale.model.OrderStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 只负责订单 SQL 和行映射，不决定哪些状态转换是合法的。 */
@Repository
public class OrderRepository {
  private final JdbcTemplate jdbc;

  public OrderRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Order> findByKey(String userId, String key) {
    return jdbc
        .query(
            "SELECT * FROM sale_orders WHERE user_id=? AND idempotency_key=?",
            OrderRepository::mapRow,
            userId,
            key)
        .stream()
        .findFirst();
  }

  public Optional<Order> findByIdForUser(String id, String userId) {
    return jdbc
        .query(
            "SELECT * FROM sale_orders WHERE id=? AND user_id=?",
            OrderRepository::mapRow,
            id,
            userId)
        .stream()
        .findFirst();
  }

  public Optional<Order> findByIdForUserWithLock(String id, String userId) {
    return jdbc
        .query(
            "SELECT * FROM sale_orders WHERE id=? AND user_id=? FOR UPDATE",
            OrderRepository::mapRow,
            id,
            userId)
        .stream()
        .findFirst();
  }

  public List<Order> findLatestForUser(String userId) {
    return jdbc.query(
        "SELECT * FROM sale_orders WHERE user_id=? ORDER BY created_at DESC LIMIT 100",
        OrderRepository::mapRow,
        userId);
  }

  public List<Order> findExpired(Instant now) {
    return jdbc.query(
        "SELECT * FROM sale_orders WHERE status='PENDING' AND expires_at<=? ORDER BY expires_at LIMIT 100",
        OrderRepository::mapRow,
        Timestamp.from(now));
  }

  public void insert(Order order, String key) {
    jdbc.update(
        "INSERT INTO sale_orders(id,user_id,product_id,quantity,total_cents,status,idempotency_key,created_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?)",
        order.id(),
        order.userId(),
        order.productId(),
        order.quantity(),
        order.totalCents(),
        order.status().name(),
        key,
        Timestamp.from(order.createdAt()),
        Timestamp.from(order.expiresAt()));
  }

  public void updateStatus(String id, OrderStatus status) {
    jdbc.update("UPDATE sale_orders SET status=? WHERE id=?", status.name(), id);
  }

  private static Order mapRow(ResultSet rs, int row) throws SQLException {
    return new Order(
        rs.getString("id"),
        rs.getString("user_id"),
        rs.getLong("product_id"),
        rs.getInt("quantity"),
        rs.getLong("total_cents"),
        OrderStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("expires_at").toInstant());
  }
}

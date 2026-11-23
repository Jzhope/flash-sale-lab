package dev.jzhope.flashsale.repository;

import dev.jzhope.flashsale.model.Notification;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 保存和查询通知；event_id 主键是重复消息的最终去重约束。 */
@Repository
public class NotificationRepository {
  private final JdbcTemplate jdbc;

  public NotificationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(String userId, Notification notification) {
    jdbc.update(
        "INSERT INTO notifications(event_id,user_id,order_id,event_type,created_at) VALUES (?,?,?,?,?)",
        notification.eventId(),
        userId,
        notification.orderId(),
        notification.type(),
        Timestamp.from(notification.createdAt()));
  }

  public List<Notification> findLatestForUser(String userId) {
    return jdbc.query(
        "SELECT event_id,order_id,event_type,created_at FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 100",
        (rs, row) ->
            new Notification(
                rs.getString("event_id"),
                rs.getString("order_id"),
                rs.getString("event_type"),
                rs.getTimestamp("created_at").toInstant()),
        userId);
  }
}

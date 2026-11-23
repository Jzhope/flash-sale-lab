package dev.jzhope.flashsale.event;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 查询当前用户的站内通知；通知由 Kafka 消费者异步创建。 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final JdbcTemplate db;

  public NotificationController(JdbcTemplate db) {
    this.db = db;
  }

  /** 返回给客户端的通知快照，不包含其他用户的数据。 */
  public record Notification(String eventId, String orderId, String type, Instant createdAt) {}

  @GetMapping
  public List<Notification> list(Principal user) {
    return db.query(
        "SELECT event_id,order_id,event_type,created_at FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 100",
        (rs, i) ->
            new Notification(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()),
        user.getName());
  }
}

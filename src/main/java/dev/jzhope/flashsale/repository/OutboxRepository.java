package dev.jzhope.flashsale.repository;

import dev.jzhope.flashsale.model.OutboxEvent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 保存和领取 Outbox 事件，调用方事务决定行锁的有效期。 */
@Repository
public class OutboxRepository {
  private final JdbcTemplate jdbc;

  public OutboxRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(OutboxEvent event, Instant createdAt) {
    jdbc.update(
        "INSERT INTO outbox_events(id,aggregate_id,payload,created_at) VALUES (?,?,?,?)",
        event.id(),
        event.orderId(),
        event.payload(),
        Timestamp.from(createdAt));
  }

  public List<OutboxEvent> findPendingWithLock() {
    return jdbc.query(
        "SELECT id,aggregate_id,payload FROM outbox_events WHERE published_at IS NULL ORDER BY created_at,id LIMIT 10 FOR UPDATE SKIP LOCKED",
        (rs, row) ->
            new OutboxEvent(
                rs.getString("id"), rs.getString("aggregate_id"), rs.getString("payload")));
  }

  public void markPublished(String id, Instant publishedAt) {
    jdbc.update(
        "UPDATE outbox_events SET published_at=? WHERE id=?", Timestamp.from(publishedAt), id);
  }
}

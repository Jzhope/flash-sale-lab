package dev.jzhope.flashsale.event;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在 broker 确认接收后标记事件，提供至少一次投递；消费者必须支持重复消息。 */
@Service
@ConditionalOnProperty(name = "app.messaging-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
  private final JdbcTemplate db;
  private final KafkaTemplate<String, String> kafka;
  private final Clock clock;

  public OutboxPublisher(JdbcTemplate db, KafkaTemplate<String, String> kafka, Clock clock) {
    this.db = db;
    this.kafka = kafka;
    this.clock = clock;
  }

  private record Pending(String id, String orderId, String payload) {}

  @Transactional
  public int publishBatch() {
    // 行锁保持到 broker ACK 和 SQL 提交，其他副本通过 SKIP LOCKED 领取不同事件。
    var batch =
        db.query(
            "SELECT id,aggregate_id,payload FROM outbox_events WHERE published_at IS NULL ORDER BY created_at,id LIMIT 10 FOR UPDATE SKIP LOCKED",
            (rs, i) -> new Pending(rs.getString(1), rs.getString(2), rs.getString(3)));
    for (var event : batch) {
      try {
        kafka.send(KafkaConfig.TOPIC, event.orderId(), event.payload()).get(12, TimeUnit.SECONDS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Outbox interrupted", ex);
      } catch (Exception ex) {
        throw new IllegalStateException("Broker did not acknowledge event " + event.id(), ex);
      }
      // ACK 后、SQL 提交前若进程崩溃，事件会重发，因此不能宣称全局 exactly-once。
      db.update(
          "UPDATE outbox_events SET published_at=? WHERE id=?",
          Timestamp.from(clock.instant()),
          event.id());
    }
    return batch.size();
  }
}

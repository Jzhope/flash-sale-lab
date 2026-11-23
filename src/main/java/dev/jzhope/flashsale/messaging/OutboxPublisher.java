package dev.jzhope.flashsale.messaging;

import dev.jzhope.flashsale.config.KafkaConfig;
import dev.jzhope.flashsale.repository.OutboxRepository;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** broker 确认后才标记发送成功，提供至少一次投递；消费者必须处理重复消息。 */
@Service
@ConditionalOnProperty(name = "app.messaging-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
  private final OutboxRepository outbox;
  private final KafkaTemplate<String, String> kafka;
  private final Clock clock;

  public OutboxPublisher(
      OutboxRepository outbox, KafkaTemplate<String, String> kafka, Clock clock) {
    this.outbox = outbox;
    this.kafka = kafka;
    this.clock = clock;
  }

  @Transactional
  public int publishBatch() {
    // 行锁保持到 ACK 和 SQL 提交，其他副本通过 SKIP LOCKED 领取不同事件。
    var batch = outbox.findPendingWithLock();
    for (var event : batch) {
      try {
        kafka.send(KafkaConfig.TOPIC, event.orderId(), event.payload()).get(12, TimeUnit.SECONDS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Outbox interrupted", ex);
      } catch (Exception ex) {
        throw new IllegalStateException("Broker did not acknowledge event " + event.id(), ex);
      }
      // ACK 后、SQL 提交前若崩溃，事件会重发，因此不能宣称全局 exactly-once。
      outbox.markPublished(event.id(), clock.instant());
    }
    return batch.size();
  }
}

package dev.jzhope.flashsale.task;

import dev.jzhope.flashsale.messaging.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 调度 Outbox 发布；失败批次留在数据库，下次轮询继续处理。 */
@Component
@ConditionalOnProperty(name = "app.messaging-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxJob {
  private static final Logger log = LoggerFactory.getLogger(OutboxJob.class);
  private final OutboxPublisher publisher;

  public OutboxJob(OutboxPublisher publisher) {
    this.publisher = publisher;
  }

  @Scheduled(
      initialDelayString = "${app.outbox-delay-ms}",
      fixedDelayString = "${app.outbox-delay-ms}")
  public void publish() {
    try {
      publisher.publishBatch();
    } catch (RuntimeException ex) {
      log.warn("Outbox batch retained for retry: {}", ex.getMessage());
    }
  }
}

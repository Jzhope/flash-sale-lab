package dev.jzhope.flashsale.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jzhope.flashsale.config.KafkaConfig;
import dev.jzhope.flashsale.service.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka 适配层：解析和校验消息，再交给通知 Service 执行业务事务。 */
@Component
@ConditionalOnProperty(name = "app.messaging-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationConsumer {
  private final NotificationService notifications;
  private final ObjectMapper json;

  public NotificationConsumer(NotificationService notifications, ObjectMapper json) {
    this.notifications = notifications;
    this.json = json;
  }

  @KafkaListener(topics = KafkaConfig.TOPIC, concurrency = "3")
  public void receive(String payload) throws JsonProcessingException {
    OrderEvent event = json.readValue(payload, OrderEvent.class);
    if (event == null
        || event.eventId() == null
        || event.eventId().length() != 36
        || event.orderId() == null
        || event.orderId().length() != 36
        || event.userId() == null
        || event.userId().isBlank()
        || event.userId().length() > 36
        || event.occurredAt() == null
        || event.type() == null) {
      throw new IllegalArgumentException("Invalid order event");
    }
    // Service 的数据库事务提交后这里才返回，然后监听容器提交 Kafka offset。
    notifications.record(event);
  }
}

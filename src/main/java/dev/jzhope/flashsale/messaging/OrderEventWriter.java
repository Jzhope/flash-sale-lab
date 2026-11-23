package dev.jzhope.flashsale.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jzhope.flashsale.model.OutboxEvent;
import dev.jzhope.flashsale.repository.OutboxRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 将事件写入调用方事务；MANDATORY 防止业务提交和事件保存脱离同一个事务。 */
@Component
public class OrderEventWriter {
  private final OutboxRepository outbox;
  private final ObjectMapper json;
  private final Clock clock;

  public OrderEventWriter(OutboxRepository outbox, ObjectMapper json, Clock clock) {
    this.outbox = outbox;
    this.json = json;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void append(OrderEventType type, String orderId, String userId) {
    var event =
        new OrderEvent(UUID.randomUUID().toString(), type, orderId, userId, clock.instant());
    try {
      outbox.insert(
          new OutboxEvent(event.eventId(), orderId, json.writeValueAsString(event)),
          event.occurredAt());
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize order event", ex);
    }
  }
}

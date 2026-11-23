package dev.jzhope.flashsale.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @ConditionalOnProperty(name="app.messaging-enabled",havingValue="true",matchIfMissing=true)
public class NotificationConsumer {
    private static final Set<String> TYPES=Set.of("ORDER_CREATED","ORDER_PAID","ORDER_CANCELLED","ORDER_EXPIRED");
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public NotificationConsumer(JdbcTemplate db,ObjectMapper json) { this.db=db; this.json=json; }
    @KafkaListener(topics=KafkaConfig.TOPIC,concurrency="3")
    @Transactional
    public void receive(String payload) throws JsonProcessingException {
        OrderEvent event=json.readValue(payload,OrderEvent.class);
        if(event==null || event.eventId()==null || event.eventId().length()!=36 || event.orderId()==null || event.orderId().length()!=36
                || event.userId()==null || event.userId().isBlank() || event.userId().length()>36 || event.occurredAt()==null || event.type()==null || !TYPES.contains(event.type()))
            throw new IllegalArgumentException("Invalid order event");
        try {
            db.update("INSERT INTO notifications(event_id,user_id,order_id,event_type,created_at) VALUES (?,?,?,?,?)",
                    event.eventId(),event.userId(),event.orderId(),event.type(),Timestamp.from(event.occurredAt()));
        } catch(DuplicateKeyException duplicate) { /* The event ID makes redelivery harmless. */ }
    }
}

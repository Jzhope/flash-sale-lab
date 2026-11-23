package dev.jzhope.flashsale.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EventStore {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Clock clock;
    public EventStore(JdbcTemplate db, ObjectMapper json, Clock clock) { this.db=db; this.json=json; this.clock=clock; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String type, String orderId, String userId) {
        var event = new OrderEvent(UUID.randomUUID().toString(), type, orderId, userId, clock.instant());
        try {
            db.update("INSERT INTO outbox_events(id,aggregate_id,payload,created_at) VALUES (?,?,?,?)",
                    event.eventId(), orderId, json.writeValueAsString(event), Timestamp.from(event.occurredAt()));
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize order event", ex); }
    }
}

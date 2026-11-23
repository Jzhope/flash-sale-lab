package dev.jzhope.flashsale.event;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @ConditionalOnProperty(name="app.messaging-enabled",havingValue="true",matchIfMissing=true)
public class OutboxPublisher {
    private final JdbcTemplate db;
    private final KafkaTemplate<String,String> kafka;
    private final Clock clock;
    public OutboxPublisher(JdbcTemplate db,KafkaTemplate<String,String> kafka,Clock clock) { this.db=db; this.kafka=kafka; this.clock=clock; }
    private record Pending(String id,String orderId,String payload) {}
    @Transactional
    public int publishBatch() {
        // Locks live until the broker ACK and SQL commit; other replicas skip these rows.
        var batch=db.query("SELECT id,aggregate_id,payload FROM outbox_events WHERE published_at IS NULL ORDER BY created_at,id LIMIT 10 FOR UPDATE SKIP LOCKED",
                (rs,i) -> new Pending(rs.getString(1),rs.getString(2),rs.getString(3)));
        for(var event:batch) {
            try { kafka.send(KafkaConfig.TOPIC,event.orderId(),event.payload()).get(12,TimeUnit.SECONDS); }
            catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Outbox interrupted",ex); }
            catch(Exception ex) { throw new IllegalStateException("Broker did not acknowledge event "+event.id(),ex); }
            db.update("UPDATE outbox_events SET published_at=? WHERE id=?",Timestamp.from(clock.instant()),event.id());
        }
        return batch.size();
    }
}

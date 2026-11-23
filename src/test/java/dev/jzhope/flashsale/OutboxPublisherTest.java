package dev.jzhope.flashsale;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import dev.jzhope.flashsale.event.OutboxPublisher;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxPublisherTest {
    @Test @SuppressWarnings({"unchecked","rawtypes"})
    void brokerFailureDoesNotMarkEventPublished() throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
        when(db.query(anyString(),any(RowMapper.class))).thenAnswer(invocation->{
            ResultSet row=mock(ResultSet.class);
            when(row.getString(1)).thenReturn("event-id"); when(row.getString(2)).thenReturn("order-id"); when(row.getString(3)).thenReturn("payload");
            return List.of(((RowMapper)invocation.getArgument(1)).mapRow(row,0));
        });
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        assertThatThrownBy(()->new OutboxPublisher(db,kafka,Clock.systemUTC()).publishBatch()).hasMessageContaining("did not acknowledge");
        verify(db,never()).update(anyString(),any(),any());
    }
}

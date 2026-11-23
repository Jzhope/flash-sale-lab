package dev.jzhope.flashsale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jzhope.flashsale.messaging.OutboxPublisher;
import dev.jzhope.flashsale.model.OutboxEvent;
import dev.jzhope.flashsale.repository.OutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxPublisherTest {
  @Test
  @SuppressWarnings("unchecked")
  void brokerFailureDoesNotMarkEventPublished() {
    OutboxRepository outbox = mock(OutboxRepository.class);
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(outbox.findPendingWithLock())
        .thenReturn(List.of(new OutboxEvent("event-id", "order-id", "payload")));
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
    assertThatThrownBy(() -> new OutboxPublisher(outbox, kafka, Clock.systemUTC()).publishBatch())
        .hasMessageContaining("did not acknowledge");
    verify(outbox, never()).markPublished(anyString(), any(Instant.class));
  }
}

package dev.jzhope.flashsale.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** 声明订单与死信主题，并设置消费重试及显式的死信目标。 */
@Configuration
@ConditionalOnProperty(name = "app.messaging-enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {
  public static final String TOPIC = "order-events";

  @Bean
  NewTopic orderEvents() {
    return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic deadLetters() {
    return TopicBuilder.name(TOPIC + ".DLT").partitions(3).replicas(1).build();
  }

  @Bean
  DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, exception) ->
                new org.apache.kafka.common.TopicPartition(TOPIC + ".DLT", record.partition()));
    // 死信发布失败时继续重试，不能因为恢复流程失败而丢弃原消息。
    recoverer.setFailIfSendResultIsError(true);
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
  }
}

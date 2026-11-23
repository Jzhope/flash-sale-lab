package dev.jzhope.flashsale.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration @ConditionalOnProperty(name="app.messaging-enabled",havingValue="true",matchIfMissing=true)
public class KafkaConfig {
    public static final String TOPIC="order-events";
    @Bean NewTopic orderEvents() { return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build(); }
    @Bean NewTopic deadLetters() { return TopicBuilder.name(TOPIC+".DLT").partitions(3).replicas(1).build(); }
    @Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,String> template) {
        var recoverer=new DeadLetterPublishingRecoverer(template,
                (record,exception)->new org.apache.kafka.common.TopicPartition(TOPIC+".DLT",record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer,new FixedBackOff(1000L,2L));
    }
}

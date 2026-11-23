package dev.jzhope.flashsale;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jzhope.flashsale.api.ApiException;
import dev.jzhope.flashsale.auth.AuthService;
import dev.jzhope.flashsale.catalog.ProductService;
import dev.jzhope.flashsale.event.KafkaConfig;
import dev.jzhope.flashsale.event.OutboxPublisher;
import dev.jzhope.flashsale.order.OrderService;
import dev.jzhope.flashsale.order.RateLimiter;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties={"app.outbox-delay-ms=3600000","app.expiry-delay-ms=3600000"})
@Testcontainers @EnabledIfSystemProperty(named="integration",matches="true")
class MiddlewareIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @Container static final KafkaContainer KAFKA=new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.2"));
    @DynamicPropertySource static void configure(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url",MYSQL::getJdbcUrl);
        props.add("spring.datasource.username",MYSQL::getUsername);
        props.add("spring.datasource.password",MYSQL::getPassword);
        props.add("spring.data.redis.host",REDIS::getHost);
        props.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));
        props.add("spring.kafka.bootstrap-servers",KAFKA::getBootstrapServers);
    }
    @Autowired JdbcTemplate db;
    @Autowired AuthService auth;
    @Autowired OrderService orders;
    @Autowired OutboxPublisher publisher;
    @Autowired KafkaTemplate<String,String> kafka;
    @Autowired StringRedisTemplate redis;
    @Autowired ProductService products;
    @Autowired RateLimiter limiter;

    @Test void realSqlRedisAndKafkaDeliverOneNotificationDespiteRedelivery() throws Exception {
        db.update("INSERT INTO products(id,name,price_cents,stock) VALUES (1,'Keyboard',19900,10)");
        var token=auth.register("integration_buyer","student-password");
        String user=auth.authenticate(token.accessToken());
        assertThat(user).isNotNull();
        assertThat(products.get(1).priceCents()).isEqualTo(19900);
        assertThat(redis.opsForValue().get("product:v1:1")).contains("Keyboard");
        assertThat(products.get(1).name()).isEqualTo("Keyboard");
        for(int i=0;i<10;i++) limiter.check(user);
        assertThatThrownBy(()->limiter.check(user)).isInstanceOf(ApiException.class).hasMessageContaining("Too many");
        assertThat(redis.getExpire("rate:order:"+user)).isBetween(0L,10L);
        var order=orders.create(user,"integration-key",1,2);
        assertThat(orders.create(user,"integration-key",1,2).id()).isEqualTo(order.id());
        while(publisher.publishBatch()>0) { /* Drain all pending SQL events. */ }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(()->assertThat(notificationCount(order.id())).isEqualTo(1));
        String payload=db.queryForObject("SELECT payload FROM outbox_events WHERE aggregate_id=?",String.class,order.id());
        kafka.send(KafkaConfig.TOPIC,order.id(),payload).get(10,TimeUnit.SECONDS);
        // A later marker on the same partition proves the duplicate was consumed first.
        var marker=new dev.jzhope.flashsale.event.OrderEvent(UUID.randomUUID().toString(),"ORDER_CREATED",order.id(),user,Instant.now());
        kafka.send(KafkaConfig.TOPIC,order.id(),new ObjectMapper().findAndRegisterModules().writeValueAsString(marker)).get(10,TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(()->assertThat(notificationCount(order.id())).isEqualTo(2));
        assertThat(db.queryForObject("SELECT stock FROM products WHERE id=1",Integer.class)).isEqualTo(8);
    }
    @Test void mysqlConcurrentBuyersCannotOversell() throws Exception {
        db.update("INSERT INTO products(id,name,price_cents,stock) VALUES (99,'Concurrent Ticket',100,15)");
        List<Callable<Boolean>> tasks=new ArrayList<>();
        for(int i=0;i<40;i++) {
            String user=UUID.randomUUID().toString();
            db.update("INSERT INTO app_users(id,username,password_hash,created_at) VALUES (?,?,?,?)",user,"concurrent_"+i,"unused",Timestamp.from(Instant.now()));
            tasks.add(()-> { try { orders.create(user,"buy",99,1); return true; } catch(ApiException ex) { assertThat(ex.getMessage()).isEqualTo("Sold out"); return false; } });
        }
        var pool=Executors.newFixedThreadPool(16);
        try { int successes=0; for(var result:pool.invokeAll(tasks)) if(result.get()) successes++; assertThat(successes).isEqualTo(15); }
        finally { pool.shutdownNow(); }
        assertThat(db.queryForObject("SELECT stock FROM products WHERE id=99",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM sale_orders WHERE product_id=99",Integer.class)).isEqualTo(15);
    }
    @Test void poisonMessageIsRetainedInDeadLetterTopic() throws Exception {
        var props=new Properties();
        props.putAll(Map.of("bootstrap.servers",KAFKA.getBootstrapServers(),"group.id","dlt-test-"+UUID.randomUUID(),
                "auto.offset.reset","earliest","key.deserializer",StringDeserializer.class.getName(),"value.deserializer",StringDeserializer.class.getName()));
        String invalid="{broken-json-"+UUID.randomUUID();
        try(var consumer=new KafkaConsumer<String,String>(props)) {
            consumer.subscribe(List.of(KafkaConfig.TOPIC+".DLT"));
            kafka.send(KafkaConfig.TOPIC,"poison",invalid).get(10,TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(30)).until(()-> {
                for(var record:consumer.poll(Duration.ofMillis(500))) if(record.value().equals(invalid)) return true;
                return false;
            });
        }
    }
    private int notificationCount(String order) { return db.queryForObject("SELECT COUNT(*) FROM notifications WHERE order_id=?",Integer.class,order); }
}

package dev.jzhope.flashsale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jzhope.flashsale.exception.ApiException;
import dev.jzhope.flashsale.model.Product;
import dev.jzhope.flashsale.repository.ProductRepository;
import dev.jzhope.flashsale.service.ProductService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
  @Mock ProductRepository products;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> cache;
  private ProductService service;
  private final Product product = new Product(1, "Keyboard", 19900);

  @BeforeEach
  void setup() {
    when(redis.opsForValue()).thenReturn(cache);
    service = new ProductService(products, redis, new ObjectMapper());
  }

  @Test
  void cacheHitDoesNotQuerySql() {
    when(cache.get("product:v1:1"))
        .thenReturn("{\"id\":1,\"name\":\"Keyboard\",\"priceCents\":19900}");
    assertThat(service.get(1)).isEqualTo(product);
    verify(products, never()).findById(anyLong());
  }

  @Test
  void redisFailureFallsBackToSql() {
    when(cache.get("product:v1:1")).thenThrow(new RedisConnectionFailureException("unavailable"));
    when(products.findById(1)).thenReturn(Optional.of(product));
    assertThat(service.get(1)).isEqualTo(product);
  }

  @Test
  void jsonNullIsTreatedAsACacheMiss() {
    when(cache.get("product:v1:1")).thenReturn("null");
    when(products.findById(1)).thenReturn(Optional.of(product));
    assertThat(service.get(1)).isEqualTo(product);
  }

  @Test
  void malformedCacheEntryFallsBackToSql() {
    when(cache.get("product:v1:1")).thenReturn("{broken-json");
    when(products.findById(1)).thenReturn(Optional.of(product));
    assertThat(service.get(1)).isEqualTo(product);
  }

  @Test
  void missingProductIsNotReturnedAsAnEmptySuccess() {
    when(products.findById(1)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(1))
        .isInstanceOf(ApiException.class)
        .hasMessage("Product not found");
  }
}

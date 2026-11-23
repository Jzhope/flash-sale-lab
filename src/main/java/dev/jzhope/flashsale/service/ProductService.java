package dev.jzhope.flashsale.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jzhope.flashsale.exception.ApiException;
import dev.jzhope.flashsale.model.Product;
import dev.jzhope.flashsale.repository.ProductRepository;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 商品查询和缓存策略；SQL 由 ProductRepository 管理，库存不进入缓存。 */
@Service
public class ProductService {
  private static final Logger log = LoggerFactory.getLogger(ProductService.class);
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);
  private final ProductRepository products;
  private final StringRedisTemplate redis;
  private final ObjectMapper json;

  public ProductService(ProductRepository products, StringRedisTemplate redis, ObjectMapper json) {
    this.products = products;
    this.redis = redis;
    this.json = json;
  }

  public List<Product> list() {
    return products.findAll();
  }

  public Product get(long id) {
    String key = "product:v1:" + id;
    // 缓存不是正确性依赖：读取失败或内容无法解析时回源数据库。
    try {
      String cached = redis.opsForValue().get(key);
      if (cached != null) {
        Product product = json.readValue(cached, Product.class);
        if (product != null) {
          return product;
        }
      }
    } catch (DataAccessException | JsonProcessingException ex) {
      log.debug("Product cache unavailable; using SQL", ex);
    }
    Product product =
        products
            .findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    try {
      redis.opsForValue().set(key, json.writeValueAsString(product), CACHE_TTL);
    } catch (DataAccessException | JsonProcessingException ex) {
      log.debug("Could not populate product cache", ex);
    }
    return product;
  }
}

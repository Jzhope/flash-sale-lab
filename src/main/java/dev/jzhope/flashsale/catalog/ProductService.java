package dev.jzhope.flashsale.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jzhope.flashsale.api.ApiException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 查询商品元数据并维护可降级的 Redis 缓存；库存决策始终留在订单事务内。 */
@Service
public class ProductService {
  private static final Logger log = LoggerFactory.getLogger(ProductService.class);
  private final JdbcTemplate db;
  private final StringRedisTemplate redis;
  private final ObjectMapper json;

  public ProductService(JdbcTemplate db, StringRedisTemplate redis, ObjectMapper json) {
    this.db = db;
    this.redis = redis;
    this.json = json;
  }

  /** 商品查询结果不包含库存，防止客户端将缓存数据当作实时库存。 */
  public record Product(long id, String name, long priceCents) {}

  public List<Product> list() {
    return db.query(
        "SELECT id,name,price_cents FROM products ORDER BY id LIMIT 100",
        (rs, i) -> new Product(rs.getLong(1), rs.getString(2), rs.getLong(3)));
  }

  public Product get(long id) {
    String key = "product:v1:" + id;
    // 缓存不是正确性依赖：读取失败或内容无法解析时回源数据库。
    try {
      String cached = redis.opsForValue().get(key);
      if (cached != null) {
        return json.readValue(cached, Product.class);
      }
    } catch (DataAccessException | JsonProcessingException ex) {
      log.debug("Product cache unavailable; using SQL", ex);
    }
    var products =
        db.query(
            "SELECT id,name,price_cents FROM products WHERE id=?",
            (rs, i) -> new Product(rs.getLong(1), rs.getString(2), rs.getLong(3)),
            id);
    if (products.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Product not found");
    }
    Product product = products.get(0);
    try {
      redis.opsForValue().set(key, json.writeValueAsString(product), Duration.ofMinutes(5));
    } catch (DataAccessException | JsonProcessingException ex) {
      log.debug("Could not populate product cache", ex);
    }
    return product;
  }
}

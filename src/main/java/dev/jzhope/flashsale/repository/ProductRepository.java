package dev.jzhope.flashsale.repository;

import dev.jzhope.flashsale.model.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 商品数据访问；库存由带条件的 SQL 更新保证不会被扣成负数。 */
@Repository
public class ProductRepository {
  private final JdbcTemplate jdbc;

  public ProductRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Product> findAll() {
    return jdbc.query(
        "SELECT id,name,price_cents FROM products ORDER BY id LIMIT 100",
        (rs, row) ->
            new Product(rs.getLong("id"), rs.getString("name"), rs.getLong("price_cents")));
  }

  public Optional<Product> findById(long id) {
    return jdbc
        .query(
            "SELECT id,name,price_cents FROM products WHERE id=?",
            (rs, row) ->
                new Product(rs.getLong("id"), rs.getString("name"), rs.getLong("price_cents")),
            id)
        .stream()
        .findFirst();
  }

  public boolean reserveStock(long productId, int quantity) {
    return jdbc.update(
            "UPDATE products SET stock=stock-? WHERE id=? AND stock>=?",
            quantity,
            productId,
            quantity)
        == 1;
  }

  public void releaseStock(long productId, int quantity) {
    jdbc.update("UPDATE products SET stock=stock+? WHERE id=?", quantity, productId);
  }
}

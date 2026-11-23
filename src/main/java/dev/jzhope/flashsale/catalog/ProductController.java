package dev.jzhope.flashsale.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商品查询接口，负责 HTTP 路由，缓存与 SQL 查询由 ProductService 执行。 */
@RestController
@RequestMapping("/api/products")
public class ProductController {
  private final ProductService products;

  public ProductController(ProductService products) {
    this.products = products;
  }

  @GetMapping
  public List<ProductService.Product> list() {
    return products.list();
  }

  @GetMapping("/{id}")
  public ProductService.Product get(@PathVariable long id) {
    return products.get(id);
  }
}

package dev.jzhope.flashsale.controller;

import dev.jzhope.flashsale.dto.ProductResponse;
import dev.jzhope.flashsale.service.ProductService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商品 HTTP 入口：接收参数、调用 Service，并转换为公开响应。 */
@RestController
@RequestMapping("/api/products")
public class ProductController {
  private final ProductService products;

  public ProductController(ProductService products) {
    this.products = products;
  }

  @GetMapping
  public List<ProductResponse> list() {
    return products.list().stream().map(ProductResponse::from).toList();
  }

  @GetMapping("/{id}")
  public ProductResponse get(@PathVariable long id) {
    return ProductResponse.from(products.get(id));
  }
}

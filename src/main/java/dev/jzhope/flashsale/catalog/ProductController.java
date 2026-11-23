package dev.jzhope.flashsale.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/products")
public class ProductController {
    private final ProductService products;
    public ProductController(ProductService products) { this.products=products; }
    @GetMapping public List<ProductService.Product> list() { return products.list(); }
    @GetMapping("/{id}") public ProductService.Product get(@PathVariable long id) { return products.get(id); }
}

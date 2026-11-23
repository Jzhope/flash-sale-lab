package dev.jzhope.flashsale.dto;

import dev.jzhope.flashsale.model.Product;

/** 商品的公开响应，不提供可能过期的缓存库存。 */
public record ProductResponse(long id, String name, long priceCents) {
  public static ProductResponse from(Product product) {
    return new ProductResponse(product.id(), product.name(), product.priceCents());
  }
}

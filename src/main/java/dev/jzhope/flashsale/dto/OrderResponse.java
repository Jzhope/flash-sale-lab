package dev.jzhope.flashsale.dto;

import dev.jzhope.flashsale.model.Order;
import java.time.Instant;

/** 订单的公开 JSON 契约，独立于内部模型，避免数据库字段意外暴露。 */
public record OrderResponse(
    String id,
    String userId,
    long productId,
    int quantity,
    long totalCents,
    String status,
    Instant createdAt,
    Instant expiresAt) {
  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.id(),
        order.userId(),
        order.productId(),
        order.quantity(),
        order.totalCents(),
        order.status().name(),
        order.createdAt(),
        order.expiresAt());
  }
}

package dev.jzhope.flashsale.model;

import java.time.Instant;

/** 数据库中的订单快照；金额使用整数分，状态转换由 OrderService 管理。 */
public record Order(
    String id,
    String userId,
    long productId,
    int quantity,
    long totalCents,
    OrderStatus status,
    Instant createdAt,
    Instant expiresAt) {}

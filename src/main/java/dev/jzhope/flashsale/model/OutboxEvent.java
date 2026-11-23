package dev.jzhope.flashsale.model;

/** 等待发送的 Outbox 记录，payload 是创建订单事务中保存的 JSON。 */
public record OutboxEvent(String id, String orderId, String payload) {}

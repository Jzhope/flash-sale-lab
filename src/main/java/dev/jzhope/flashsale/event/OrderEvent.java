package dev.jzhope.flashsale.event;

import java.time.Instant;

/** 订单事件契约；eventId 在重复投递时保持不变，作为消费者的去重键。 */
public record OrderEvent(
    String eventId, String type, String orderId, String userId, Instant occurredAt) {}

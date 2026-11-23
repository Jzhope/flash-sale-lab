package dev.jzhope.flashsale.model;

import java.time.Instant;

/** 已保存的站内通知，eventId 同时作为数据库去重键。 */
public record Notification(String eventId, String orderId, String type, Instant createdAt) {}

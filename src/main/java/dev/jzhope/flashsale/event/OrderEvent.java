package dev.jzhope.flashsale.event;

import java.time.Instant;

public record OrderEvent(String eventId, String type, String orderId, String userId, Instant occurredAt) {}

package dev.jzhope.flashsale.dto;

import dev.jzhope.flashsale.model.Notification;
import java.time.Instant;

/** 当前用户能看到的站内通知响应。 */
public record NotificationResponse(String eventId, String orderId, String type, Instant createdAt) {
  public static NotificationResponse from(Notification notification) {
    return new NotificationResponse(
        notification.eventId(),
        notification.orderId(),
        notification.type(),
        notification.createdAt());
  }
}

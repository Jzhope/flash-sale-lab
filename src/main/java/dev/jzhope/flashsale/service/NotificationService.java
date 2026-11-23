package dev.jzhope.flashsale.service;

import dev.jzhope.flashsale.messaging.OrderEvent;
import dev.jzhope.flashsale.model.Notification;
import dev.jzhope.flashsale.repository.NotificationRepository;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通知业务入口，HTTP 查询和 Kafka 消费都通过本类访问通知仓储。 */
@Service
public class NotificationService {
  private final NotificationRepository notifications;

  public NotificationService(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  public List<Notification> list(String userId) {
    return notifications.findLatestForUser(userId);
  }

  /** 此方法提交后监听器才返回；重复投递依靠数据库主键去重。 */
  @Transactional
  public void record(OrderEvent event) {
    try {
      notifications.insert(
          event.userId(),
          new Notification(
              event.eventId(), event.orderId(), event.type().name(), event.occurredAt()));
    } catch (DuplicateKeyException duplicate) {
      // 已有相同 event ID 表示副作用已完成，不再生成第二条通知。
    }
  }
}

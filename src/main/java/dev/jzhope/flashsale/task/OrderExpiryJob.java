package dev.jzhope.flashsale.task;

import dev.jzhope.flashsale.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期扫描到期订单；真正的状态检查和库存释放由 OrderService 的事务完成。 */
@Component
public class OrderExpiryJob {
  private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);
  private final OrderService orders;

  public OrderExpiryJob(OrderService orders) {
    this.orders = orders;
  }

  @Scheduled(
      initialDelayString = "${app.expiry-delay-ms}",
      fixedDelayString = "${app.expiry-delay-ms}")
  public void expire() {
    for (var order : orders.expiredCandidates()) {
      try {
        orders.expire(order.userId(), order.id());
      } catch (RuntimeException ex) {
        log.warn("Expiry will retry order {}", order.id(), ex);
      }
    }
  }
}

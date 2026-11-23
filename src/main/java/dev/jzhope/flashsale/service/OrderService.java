package dev.jzhope.flashsale.service;

import dev.jzhope.flashsale.exception.ApiException;
import dev.jzhope.flashsale.messaging.OrderEventType;
import dev.jzhope.flashsale.messaging.OrderEventWriter;
import dev.jzhope.flashsale.model.Order;
import dev.jzhope.flashsale.model.OrderStatus;
import dev.jzhope.flashsale.repository.AuthRepository;
import dev.jzhope.flashsale.repository.OrderRepository;
import dev.jzhope.flashsale.repository.ProductRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 订单业务和事务入口；只协调明确命名的仓储操作，不在业务流程中穿插 SQL。 */
@Service
public class OrderService {
  private final OrderRepository orders;
  private final ProductRepository products;
  private final AuthRepository accounts;
  private final OrderEventWriter events;
  private final Clock clock;
  private final long orderTtlSeconds;

  public OrderService(
      OrderRepository orders,
      ProductRepository products,
      AuthRepository accounts,
      OrderEventWriter events,
      Clock clock,
      @Value("${app.order-ttl-seconds}") long orderTtlSeconds) {
    this.orders = orders;
    this.products = products;
    this.accounts = accounts;
    this.events = events;
    this.clock = clock;
    this.orderTtlSeconds = orderTtlSeconds;
  }

  /** 库存、订单和 Outbox 必须在同一事务中提交或回滚。 */
  @Transactional
  public Order create(String userId, String key, long productId, int quantity) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{1,64}") || quantity < 1 || quantity > 5) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid idempotency key or quantity");
    }
    // 先锁用户行，使同用户的并发重试在查询幂等记录前排队。
    accounts.lockUser(userId);
    var previous = orders.findByKey(userId, key);
    if (previous.isPresent()) {
      Order existing = previous.orElseThrow();
      if (existing.productId() != productId || existing.quantity() != quantity) {
        throw new ApiException(
            HttpStatus.CONFLICT, "Idempotency key already used for a different request");
      }
      return existing;
    }
    var product =
        products
            .findById(productId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    // 由 SQL 条件更新保证库存非负，不能先查库存再用无条件更新扣减。
    if (!products.reserveStock(productId, quantity)) {
      throw new ApiException(HttpStatus.CONFLICT, "Sold out");
    }
    Instant now = clock.instant();
    var order =
        new Order(
            UUID.randomUUID().toString(),
            userId,
            productId,
            quantity,
            Math.multiplyExact(product.priceCents(), quantity),
            OrderStatus.PENDING,
            now,
            now.plusSeconds(orderTtlSeconds));
    orders.insert(order, key);
    // 不在库存事务中等待 Kafka；同事务写入 Outbox，由发布器异步投递。
    events.append(OrderEventType.ORDER_CREATED, order.id(), userId);
    return order;
  }

  public Order get(String userId, String id) {
    return orders.findByIdForUser(id, userId).orElseThrow(OrderService::notFound);
  }

  public List<Order> list(String userId) {
    return orders.findLatestForUser(userId);
  }

  @Transactional
  public Order pay(String userId, String id) {
    return transition(userId, id, OrderStatus.PAID);
  }

  @Transactional
  public Order cancel(String userId, String id) {
    return transition(userId, id, OrderStatus.CANCELLED);
  }

  @Transactional
  public Order expire(String userId, String id) {
    return transition(userId, id, OrderStatus.EXPIRED);
  }

  public List<Order> expiredCandidates() {
    return orders.findExpired(clock.instant());
  }

  /** 由上面的公开事务方法调用；订单锁防止支付、取消和过期扫描重复释放库存。 */
  private Order transition(String userId, String id, OrderStatus requested) {
    Order order = orders.findByIdForUserWithLock(id, userId).orElseThrow(OrderService::notFound);
    if (order.status() != OrderStatus.PENDING) {
      if (order.status() == requested || requested == OrderStatus.EXPIRED) {
        return order;
      }
      throw new ApiException(HttpStatus.CONFLICT, "Order is already " + order.status());
    }
    // 支付接口自己判断到期时间，避免定时任务尚未扫描就接受超期付款。
    boolean expired = !order.expiresAt().isAfter(clock.instant());
    if (requested == OrderStatus.EXPIRED && !expired) {
      return order;
    }
    OrderStatus next = expired ? OrderStatus.EXPIRED : requested;
    orders.updateStatus(id, next);
    if (next != OrderStatus.PAID) {
      products.releaseStock(order.productId(), order.quantity());
    }
    events.append(eventType(next), id, userId);
    return new Order(
        order.id(),
        order.userId(),
        order.productId(),
        order.quantity(),
        order.totalCents(),
        next,
        order.createdAt(),
        order.expiresAt());
  }

  private static OrderEventType eventType(OrderStatus status) {
    return switch (status) {
      case PAID -> OrderEventType.ORDER_PAID;
      case CANCELLED -> OrderEventType.ORDER_CANCELLED;
      case EXPIRED -> OrderEventType.ORDER_EXPIRED;
      case PENDING -> throw new IllegalArgumentException("Pending is not a terminal transition");
    };
  }

  private static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "Order not found");
  }
}

package dev.jzhope.flashsale.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 订单接口层：获取已认证用户、校验请求、执行限流，再调用订单事务。 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderService orders;
  private final RateLimiter limiter;

  public OrderController(OrderService orders, RateLimiter limiter) {
    this.orders = orders;
    this.limiter = limiter;
  }

  /** 下单请求只接受商品和数量，价格和用户身份由服务端确定。 */
  public record CreateOrder(@Min(1) long productId, @Min(1) @Max(5) int quantity) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrderService.Order create(
      Principal user,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreateOrder request) {
    limiter.check(user.getName());
    return orders.create(user.getName(), key, request.productId(), request.quantity());
  }

  @GetMapping
  public List<OrderService.Order> list(Principal user) {
    return orders.list(user.getName());
  }

  @GetMapping("/{id}")
  public OrderService.Order get(Principal user, @PathVariable String id) {
    return orders.get(user.getName(), id);
  }

  @PostMapping("/{id}/simulate-payment")
  public OrderService.Order pay(Principal user, @PathVariable String id) {
    return orders.transition(user.getName(), id, "pay");
  }

  @PostMapping("/{id}/cancel")
  public OrderService.Order cancel(Principal user, @PathVariable String id) {
    return orders.transition(user.getName(), id, "cancel");
  }
}

package dev.jzhope.flashsale.controller;

import dev.jzhope.flashsale.dto.CreateOrderRequest;
import dev.jzhope.flashsale.dto.OrderResponse;
import dev.jzhope.flashsale.service.OrderService;
import dev.jzhope.flashsale.service.RateLimiter;
import jakarta.validation.Valid;
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

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrderResponse create(
      Principal user,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreateOrderRequest request) {
    limiter.check(user.getName());
    return OrderResponse.from(
        orders.create(user.getName(), key, request.productId(), request.quantity()));
  }

  @GetMapping
  public List<OrderResponse> list(Principal user) {
    return orders.list(user.getName()).stream().map(OrderResponse::from).toList();
  }

  @GetMapping("/{id}")
  public OrderResponse get(Principal user, @PathVariable String id) {
    return OrderResponse.from(orders.get(user.getName(), id));
  }

  @PostMapping("/{id}/simulate-payment")
  public OrderResponse pay(Principal user, @PathVariable String id) {
    return OrderResponse.from(orders.pay(user.getName(), id));
  }

  @PostMapping("/{id}/cancel")
  public OrderResponse cancel(Principal user, @PathVariable String id) {
    return OrderResponse.from(orders.cancel(user.getName(), id));
  }
}

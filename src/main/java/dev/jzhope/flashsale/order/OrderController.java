package dev.jzhope.flashsale.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    private final RateLimiter limiter;
    public OrderController(OrderService orders,RateLimiter limiter) { this.orders=orders; this.limiter=limiter; }
    public record CreateOrder(@Min(1) long productId,@Min(1) @Max(5) int quantity) {}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public OrderService.Order create(Principal user,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateOrder request) {
        limiter.check(user.getName()); return orders.create(user.getName(),key,request.productId(),request.quantity());
    }
    @GetMapping public List<OrderService.Order> list(Principal user) { return orders.list(user.getName()); }
    @GetMapping("/{id}") public OrderService.Order get(Principal user,@PathVariable String id) { return orders.get(user.getName(),id); }
    @PostMapping("/{id}/simulate-payment") public OrderService.Order pay(Principal user,@PathVariable String id) { return orders.transition(user.getName(),id,"pay"); }
    @PostMapping("/{id}/cancel") public OrderService.Order cancel(Principal user,@PathVariable String id) { return orders.transition(user.getName(),id,"cancel"); }
}

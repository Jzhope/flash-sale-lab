package dev.jzhope.flashsale.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 下单请求只接受商品和数量；价格、用户身份和订单状态由服务端确定。 */
public record CreateOrderRequest(@Min(1) long productId, @Min(1) @Max(5) int quantity) {}

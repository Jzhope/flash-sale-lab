package dev.jzhope.flashsale.model;

/** 订单状态；只有 PENDING 可以继续支付、取消或过期。 */
public enum OrderStatus {
  PENDING,
  PAID,
  CANCELLED,
  EXPIRED
}

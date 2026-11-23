package dev.jzhope.flashsale.messaging;

/** 事件名是对外消息契约，新增或修改时需要同步消费者与兼容性测试。 */
public enum OrderEventType {
  ORDER_CREATED,
  ORDER_PAID,
  ORDER_CANCELLED,
  ORDER_EXPIRED
}

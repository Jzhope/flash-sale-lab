package dev.jzhope.flashsale.model;

/** 商品元数据；库存由 Repository 的条件更新维护，不从缓存中读取。 */
public record Product(long id, String name, long priceCents) {}

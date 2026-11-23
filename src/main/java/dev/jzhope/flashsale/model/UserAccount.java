package dev.jzhope.flashsale.model;

/** 仅供认证使用的账号数据，含密码摘要，不能直接作为 API 响应。 */
public record UserAccount(String id, String username, String passwordHash) {}

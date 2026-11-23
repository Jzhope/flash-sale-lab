package dev.jzhope.flashsale.dto;

import java.time.Instant;

/** 签发时返回的认证结果；原始 token 不保存到数据库。 */
public record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {}

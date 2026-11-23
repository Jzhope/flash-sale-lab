package dev.jzhope.flashsale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 注册和登录请求；ASCII 密码限制避免超过 BCrypt 的字节长度限制。 */
public record CredentialsRequest(
    @NotNull @Pattern(regexp = "[a-z0-9_]{3,32}") String username,
    @NotNull @Size(min = 8, max = 64) @Pattern(regexp = "[\\x20-\\x7E]+") String password) {}

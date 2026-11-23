package dev.jzhope.flashsale.service;

import dev.jzhope.flashsale.dto.TokenResponse;
import dev.jzhope.flashsale.exception.ApiException;
import dev.jzhope.flashsale.model.UserAccount;
import dev.jzhope.flashsale.repository.AuthRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理账号和随机 token；Repository 负责 SQL，本类负责加密、认证与事务。 */
@Service
public class AuthService {
  private final AuthRepository accounts;
  private final Clock clock;
  private final long tokenTtlHours;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();
  private final String dummyHash = passwords.encode(UUID.randomUUID().toString());

  public AuthService(
      AuthRepository accounts, Clock clock, @Value("${app.auth-ttl-hours}") long tokenTtlHours) {
    this.accounts = accounts;
    this.clock = clock;
    this.tokenTtlHours = tokenTtlHours;
  }

  @Transactional
  public TokenResponse register(String username, String password) {
    var account =
        new UserAccount(UUID.randomUUID().toString(), username, passwords.encode(password));
    try {
      accounts.insertUser(account, clock.instant());
    } catch (DuplicateKeyException ex) {
      throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
    }
    return issueToken(account.id());
  }

  @Transactional
  public TokenResponse login(String username, String password) {
    var account = accounts.findByUsername(username);
    // 不存在的用户也执行 BCrypt，减少通过响应耗时推测账号存在性的机会。
    String storedHash = account.map(UserAccount::passwordHash).orElse(dummyHash);
    boolean matches = passwords.matches(password, storedHash);
    if (account.isEmpty() || !matches) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
    return issueToken(account.orElseThrow().id());
  }

  public String authenticate(String token) {
    if (token.length() != 43) {
      return null;
    }
    return accounts.findUserByActiveToken(digest(token), clock.instant()).orElse(null);
  }

  public void logout(String token) {
    accounts.deleteToken(digest(token));
  }

  private TokenResponse issueToken(String userId) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expiresAt = clock.instant().plus(tokenTtlHours, ChronoUnit.HOURS);
    // 数据库只保存摘要，不能通过读取数据库直接得到可用的 Bearer token。
    accounts.insertToken(digest(token), userId, expiresAt);
    return new TokenResponse(token, "Bearer", expiresAt);
  }

  private static String digest(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}

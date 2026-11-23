package dev.jzhope.flashsale.auth;

import dev.jzhope.flashsale.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理账号和可撤销的随机 token；凭据保存在共享数据库，支持多个应用副本。 */
@Service
public class AuthService {
  private final JdbcTemplate db;
  private final Clock clock;
  private final long ttlHours;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();
  private final String dummyHash = passwords.encode(UUID.randomUUID().toString());

  public AuthService(JdbcTemplate db, Clock clock, @Value("${app.auth-ttl-hours}") long ttlHours) {
    this.db = db;
    this.clock = clock;
    this.ttlHours = ttlHours;
  }

  /** 返回给客户端的认证结果；原始 token 只在签发时返回，不写入数据库。 */
  public record Token(String accessToken, String tokenType, Instant expiresAt) {}

  private record User(String id, String hash) {}

  @Transactional
  public Token register(String username, String password) {
    String id = UUID.randomUUID().toString();
    try {
      db.update(
          "INSERT INTO app_users(id,username,password_hash,created_at) VALUES (?,?,?,?)",
          id,
          username,
          passwords.encode(password),
          Timestamp.from(clock.instant()));
    } catch (DuplicateKeyException ex) {
      throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
    }
    return issue(id);
  }

  @Transactional
  public Token login(String username, String password) {
    var users =
        db.query(
            "SELECT id,password_hash FROM app_users WHERE username=?",
            (rs, i) -> new User(rs.getString(1), rs.getString(2)),
            username);
    // 不存在的用户也执行 BCrypt，减少通过响应耗时推测账号存在性的机会。
    boolean matches =
        passwords.matches(password, users.isEmpty() ? dummyHash : users.get(0).hash());
    if (users.isEmpty() || !matches) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
    return issue(users.get(0).id());
  }

  private Token issue(String userId) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expires = clock.instant().plus(ttlHours, ChronoUnit.HOURS);
    // 数据库只保存摘要；读取数据库不能直接得到可用的 Bearer token。
    db.update(
        "INSERT INTO auth_tokens(token_hash,user_id,expires_at) VALUES (?,?,?)",
        digest(token),
        userId,
        Timestamp.from(expires));
    return new Token(token, "Bearer", expires);
  }

  public String authenticate(String token) {
    if (token.length() != 43) {
      return null;
    }
    var users =
        db.queryForList(
            "SELECT user_id FROM auth_tokens WHERE token_hash=? AND expires_at>?",
            String.class,
            digest(token),
            Timestamp.from(clock.instant()));
    return users.isEmpty() ? null : users.get(0);
  }

  public void logout(String token) {
    db.update("DELETE FROM auth_tokens WHERE token_hash=?", digest(token));
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

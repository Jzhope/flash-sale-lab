package dev.jzhope.flashsale.repository;

import dev.jzhope.flashsale.model.UserAccount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 封装用户与 token 的 SQL；加密、认证和事务边界由 AuthService 管理。 */
@Repository
public class AuthRepository {
  private final JdbcTemplate jdbc;

  public AuthRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insertUser(UserAccount user, Instant createdAt) {
    jdbc.update(
        "INSERT INTO app_users(id,username,password_hash,created_at) VALUES (?,?,?,?)",
        user.id(),
        user.username(),
        user.passwordHash(),
        Timestamp.from(createdAt));
  }

  public Optional<UserAccount> findByUsername(String username) {
    return jdbc
        .query(
            "SELECT id,username,password_hash FROM app_users WHERE username=?",
            (rs, row) ->
                new UserAccount(
                    rs.getString("id"), rs.getString("username"), rs.getString("password_hash")),
            username)
        .stream()
        .findFirst();
  }

  /** 调用方必须已有事务，锁会一直保持到该事务结束。 */
  public void lockUser(String userId) {
    jdbc.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE", String.class, userId);
  }

  public void insertToken(String hash, String userId, Instant expiresAt) {
    jdbc.update(
        "INSERT INTO auth_tokens(token_hash,user_id,expires_at) VALUES (?,?,?)",
        hash,
        userId,
        Timestamp.from(expiresAt));
  }

  public Optional<String> findUserByActiveToken(String hash, Instant now) {
    return jdbc
        .queryForList(
            "SELECT user_id FROM auth_tokens WHERE token_hash=? AND expires_at>?",
            String.class,
            hash,
            Timestamp.from(now))
        .stream()
        .findFirst();
  }

  public void deleteToken(String hash) {
    jdbc.update("DELETE FROM auth_tokens WHERE token_hash=?", hash);
  }
}

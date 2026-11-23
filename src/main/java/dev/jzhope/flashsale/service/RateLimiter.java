package dev.jzhope.flashsale.service;

import dev.jzhope.flashsale.exception.ApiException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 按用户执行共享 Redis 固定窗口限流；Redis 故障时拒绝新下单请求。 */
@Component
public class RateLimiter {
  // 递增和首次设置 TTL 必须原子执行，否则进程崩溃可能留下永不过期的计数器。
  private static final DefaultRedisScript<Long> SCRIPT =
      new DefaultRedisScript<>(
          """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """,
          Long.class);
  private final StringRedisTemplate redis;
  private final int requests;
  private final int seconds;

  public RateLimiter(
      StringRedisTemplate redis,
      @Value("${app.rate-limit.requests}") int requests,
      @Value("${app.rate-limit.window-seconds}") int seconds) {
    this.redis = redis;
    this.requests = requests;
    this.seconds = seconds;
  }

  public void check(String userId) {
    Long count;
    try {
      count = redis.execute(SCRIPT, List.of("rate:order:" + userId), String.valueOf(seconds));
    } catch (DataAccessException ex) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "Order admission temporarily unavailable");
    }
    if (count == null) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "Order admission temporarily unavailable");
    }
    if (count > requests) {
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          "Too many order attempts; retry after the rate-limit window");
    }
  }
}

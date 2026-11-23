package dev.jzhope.flashsale.security;

import dev.jzhope.flashsale.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在 Controller 执行前校验 token，并把数据库用户 ID 设置为当前 principal。 */
public class BearerTokenFilter extends OncePerRequestFilter {
  private final AuthService auth;

  // 不加 @Component：由 SecurityConfig 注册一次，避免成为两个过滤器链中的重复过滤器。
  public BearerTokenFilter(AuthService auth) {
    this.auth = auth;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String userId;
      try {
        userId = auth.authenticate(header.substring(7));
      } catch (DataAccessException ex) {
        writeProblem(response, 503, "Authentication storage unavailable");
        return;
      }
      if (userId == null) {
        writeProblem(response, 401, "Invalid or expired token");
        return;
      }
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
    chain.doFilter(request, response);
  }

  /** 过滤器错误发生在 ControllerAdvice 之前，需要直接写出同样的错误格式。 */
  public static void writeProblem(HttpServletResponse response, int status, String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
  }
}

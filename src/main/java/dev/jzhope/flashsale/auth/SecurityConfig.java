package dev.jzhope.flashsale.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** 定义公开接口与 Bearer 认证过滤器，在进入 Controller 前建立当前用户身份。 */
@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http, AuthService auth) throws Exception {
    // 接口只通过 Authorization 请求头接收凭据，不使用浏览器自动携带的认证 cookie。
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/products/**",
                        "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(
                    (req, res, ex) -> problem(res, 401, "Authentication required")))
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws ServletException, IOException {
                String header = req.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                  String id;
                  try {
                    id = auth.authenticate(header.substring(7));
                  } catch (DataAccessException ex) {
                    problem(res, 503, "Authentication storage unavailable");
                    return;
                  }
                  if (id == null) {
                    problem(res, 401, "Invalid or expired token");
                    return;
                  }
                  // principal 使用数据库用户 ID，后续 Controller 不接受客户端指定用户身份。
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          new UsernamePasswordAuthenticationToken(id, null, List.of()));
                }
                chain.doFilter(req, res);
              }
            },
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private static void problem(HttpServletResponse response, int status, String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
  }
}

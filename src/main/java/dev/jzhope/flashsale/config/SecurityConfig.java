package dev.jzhope.flashsale.config;

import dev.jzhope.flashsale.security.BearerTokenFilter;
import dev.jzhope.flashsale.service.AuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** 定义哪些接口公开，以及 Bearer token 过滤器在安全链中的位置。 */
@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http, AuthService auth) throws Exception {
    // 凭据仅来自 Authorization 请求头，不使用浏览器自动携带的认证 cookie。
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
                    (request, response, exception) ->
                        BearerTokenFilter.writeProblem(response, 401, "Authentication required")))
        .addFilterBefore(new BearerTokenFilter(auth), UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}

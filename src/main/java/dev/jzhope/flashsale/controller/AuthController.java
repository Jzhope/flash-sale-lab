package dev.jzhope.flashsale.controller;

import dev.jzhope.flashsale.dto.CredentialsRequest;
import dev.jzhope.flashsale.dto.TokenResponse;
import dev.jzhope.flashsale.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口层：校验请求，将密码与 token 的处理交给 AuthService。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public TokenResponse register(@Valid @RequestBody CredentialsRequest request) {
    return auth.register(request.username(), request.password());
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody CredentialsRequest request) {
    return auth.login(request.username(), request.password());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@RequestHeader("Authorization") String authorization) {
    auth.logout(authorization.substring(7));
  }
}

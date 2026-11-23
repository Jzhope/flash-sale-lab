package dev.jzhope.flashsale.api;

import org.springframework.http.HttpStatus;

/** 可预期的业务错误，携带供统一异常处理器转换的 HTTP 状态。 */
public class ApiException extends RuntimeException {
  private final HttpStatus status;

  public ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}

package dev.jzhope.flashsale.controller;

import dev.jzhope.flashsale.dto.NotificationResponse;
import dev.jzhope.flashsale.service.NotificationService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 只通过 Service 查询当前用户的通知，不在 HTTP 层直接访问数据库。 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService notifications;

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @GetMapping
  public List<NotificationResponse> list(Principal user) {
    return notifications.list(user.getName()).stream().map(NotificationResponse::from).toList();
  }
}

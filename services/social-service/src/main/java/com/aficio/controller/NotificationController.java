package com.aficio.controller;

import java.util.List;

import com.aficio.config.UserClient;
import com.aficio.dto.user.UserSummaryResponse;
import com.aficio.notification.Notification;
import com.aficio.notification.NotificationRepository;
import com.aficio.notification.NotificationResponse;
import com.aficio.notification.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserClient userClient;
    private final NotificationService notificationService;

    public NotificationController(NotificationRepository notificationRepository, UserClient userClient, NotificationService notificationService) {
        this.notificationRepository = notificationRepository;
        this.userClient = userClient;
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> getNotifications(Authentication authentication) {
        Long userId = currentUserId(authentication);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread-count")
    public long unreadCount(Authentication authentication) {
        Long userId = currentUserId(authentication);
        return notificationService.getUnreadCount(userId);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserId(authentication);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.getUserId().equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Not your notification");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        notificationService.resetUnreadCount(userId);
    }

    private Long currentUserId(Authentication authentication) {
        UserSummaryResponse userInfo = userClient.findByUsername(authentication.getName());
        return userInfo.id();
    }
}


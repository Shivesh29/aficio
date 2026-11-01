package com.aficio.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String message,
        String data,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getData(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
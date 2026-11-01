package com.aficio.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String UNREAD_COUNT_KEY_PREFIX = "notification:unread-count:";
    private static final String REDIS_CHANNEL = "notifications";

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public NotificationService(
            SimpMessagingTemplate messagingTemplate,
            RedisTemplate<String, Object> redisTemplate,
            NotificationRepository notificationRepository,
            ObjectMapper objectMapper
    ) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    public Notification createNotification(Long recipientId, String recipientUsername, String actorUsername, String type, String data) {
        if (recipientId == null) {
            return null;
        }

        String message = switch (type) {
            case "FOLLOW" -> actorUsername + " started following you";
            case "LIKE" -> actorUsername + " liked your review";
            case "COMMENT" -> actorUsername + " commented on your review";
            case "DM" -> actorUsername + " sent you a message";
            default -> "You have a new notification";
        };

        Notification notification = notificationRepository.save(new Notification(recipientId, type, message, data));
        incrementUnreadCount(recipientId);

        // Send via WebSocket directly and via Redis for cluster distribution
        sendToUser(recipientUsername, new NotificationMessage(type, message, data));
        publishToRedis(notification, recipientUsername);

        return notification;
    }

    public void sendToUser(String username, NotificationMessage message) {
        if (username != null && !username.isBlank()) {
            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/notifications",
                    message
            );
            log.debug("Sent notification to user {}: {}", username, message.type());
        }
    }

    public long getUnreadCount(Long userId) {
        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof Number num) {
            return num.longValue();
        }
        return notificationRepository.countByUserIdAndRead(userId, false);
    }

    public void incrementUnreadCount(Long userId) {
        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        redisTemplate.opsForValue().increment(key);
        log.debug("Incremented unread count for user {}", userId);
    }

    public void resetUnreadCount(Long userId) {
        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        log.debug("Reset unread count for user {}", userId);
    }

    public void publishToRedis(Notification notification, String recipientUsername) {
        try {
            var payload = new RedisNotificationPayload(
                    notification.getUserId(),
                    recipientUsername,
                    notification.getType(),
                    notification.getMessage(),
                    notification.getData(),
                    notification.getCreatedAt().toString()
            );
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                connection.publish(
                        REDIS_CHANNEL.getBytes(StandardCharsets.UTF_8),
                        json.getBytes(StandardCharsets.UTF_8)
                );
                return null;
            });
            log.debug("Published notification {} to Redis channel {}", notification.getId(), REDIS_CHANNEL);
        } catch (Exception e) {
            log.warn("Failed to publish notification to Redis, notificationId={}, error: {}", notification.getId(), e.getMessage());
        }
    }

    public record NotificationMessage(
            String type,
            String message,
            Object data
    ) {
    }

    public record RedisNotificationPayload(
            Long userId,
            String username,
            String type,
            String message,
            String data,
            String timestamp
    ) {
    }
}


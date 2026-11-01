package com.aficio.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String UNREAD_COUNT_KEY_PREFIX = "notification:unread-count:";

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate, RedisTemplate<String, Object> redisTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
    }

    public void sendToUser(String username, NotificationMessage message) {
        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/notifications",
                message
        );
        log.debug("Sent notification to user {}: {}", username, message.type());
    }

    public long getUnreadCount(Long userId) {
        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof Long count) {
            return count;
        }
        return 0;
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

    public record NotificationMessage(
            String type,
            String message,
            Object data
    ) {
    }
}
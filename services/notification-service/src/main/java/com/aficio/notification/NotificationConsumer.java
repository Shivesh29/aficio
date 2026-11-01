package com.aficio.notification;

import com.aficio.kafka.SocialEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aficio.kafka.enabled", havingValue = "true")
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    private static final String REDIS_CHANNEL = "notifications";

    private final NotificationRepository notificationRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationConsumer(
            NotificationRepository notificationRepository,
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            NotificationService notificationService
    ) {
        this.notificationRepository = notificationRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "aficio.social.events",
            groupId = "${spring.kafka.consumer.group-id:aficio}"
    )
    public void consume(String payload) {
        try {
            SocialEvent event = objectMapper.readValue(payload, SocialEvent.class);
            log.info("Received social event: type={}, recipientId={}", event.type(), event.recipientId());

            Long recipientId = event.recipientId();
            if (recipientId != null) {
                String message = buildMessage(event);
                Notification notification = notificationRepository.save(
                        new Notification(recipientId, event.type(), message, event.data())
                );
                log.info("Saved notification for user {}: {}", recipientId, notification.getId());

                notificationService.incrementUnreadCount(recipientId);
                publishToRedis(notification, event.recipientUsername());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize social event, payload: {}, error: {}", payload, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process social event, payload: {}, error: {}", payload, e.getMessage());
        }
    }

    private String buildMessage(SocialEvent event) {
        return switch (event.type()) {
            case "FOLLOW" -> event.actorUsername() + " started following you";
            case "LIKE" -> event.actorUsername() + " liked your review";
            case "COMMENT" -> event.actorUsername() + " commented on your review";
            default -> "You have a new notification";
        };
    }

    private void publishToRedis(Notification notification, String recipientUsername) {
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
                        REDIS_CHANNEL.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                return null;
            });
            log.debug("Published notification {} to Redis channel {}", notification.getId(), REDIS_CHANNEL);
        } catch (Exception e) {
            log.error("Failed to publish notification to Redis, notificationId={}, error: {}", notification.getId(), e.getMessage());
        }
    }

    private record RedisNotificationPayload(
            Long userId,
            String username,
            String type,
            String message,
            String data,
            String timestamp
    ) {
    }
}
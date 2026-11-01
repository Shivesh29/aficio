package com.aficio.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aficio.redis.enabled", havingValue = "true")
public class RedisNotificationSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisNotificationSubscriber.class);
    private static final String CHANNEL = "notifications";

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public RedisNotificationSubscriber(
            RedisMessageListenerContainer container,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        container.addMessageListener(this, new org.springframework.data.redis.listener.ChannelTopic(CHANNEL));
        log.info("Subscribed to Redis channel: {}", CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
            RedisNotificationPayload notification = objectMapper.readValue(payload, RedisNotificationPayload.class);
            log.debug("Received notification from Redis for user {}: {}", notification.userId(), notification.type());

                notificationService.sendToUser(
                    notification.username(),
                    new NotificationService.NotificationMessage(
                        notification.type(),
                        notification.message(),
                        notification.data()
                    )
                );
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize Redis notification payload: {}, error: {}", message, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to forward Redis notification to WebSocket, error: {}", e.getMessage());
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
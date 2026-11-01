package com.aficio.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aficio.kafka.enabled", havingValue = "true")
public class SocialProducer {

    private static final Logger log = LoggerFactory.getLogger(SocialProducer.class);
    private static final String TOPIC = "aficio.social.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public SocialProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendEvent(SocialEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.info("Publishing social event to topic {}: type={}, recipientId={}", TOPIC, event.type(), event.recipientId());
            kafkaTemplate.send(TOPIC, event.recipientId().toString(), payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize social event for recipientId={}, error: {}", event.recipientId(), e.getMessage());
        }
    }
}
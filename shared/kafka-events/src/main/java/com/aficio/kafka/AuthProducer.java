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
public class AuthProducer {

    private static final Logger log = LoggerFactory.getLogger(AuthProducer.class);
    private static final String TOPIC = "aficio.auth.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AuthProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendEmailVerificationEvent(EmailVerificationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.info("Publishing email verification event to topic {}: email={}", TOPIC, event.email());
            kafkaTemplate.send(TOPIC, event.userId().toString(), payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize auth event for email={}, error: {}", event.email(), e.getMessage());
        }
    }
}

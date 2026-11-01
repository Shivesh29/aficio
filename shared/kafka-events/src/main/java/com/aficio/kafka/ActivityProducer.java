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
public class ActivityProducer {

    private static final Logger log = LoggerFactory.getLogger(ActivityProducer.class);
    private static final String TOPIC = "aficio.activity.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ActivityProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendEvent(ActivityEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.info("Publishing activity event to topic {}: type={}, userId={}", TOPIC, event.type(), event.userId());
            kafkaTemplate.send(TOPIC, event.userId().toString(), payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize activity event for userId={}, error: {}", event.userId(), e.getMessage());
        }
    }
}
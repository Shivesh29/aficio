package com.aficio.activity;

import com.aficio.kafka.ActivityEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aficio.kafka.enabled", havingValue = "true")
public class ActivityConsumer {

    private static final Logger log = LoggerFactory.getLogger(ActivityConsumer.class);

    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    public ActivityConsumer(
            ActivityRepository activityRepository,
            ObjectMapper objectMapper
    ) {
        this.activityRepository = activityRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "aficio.activity.events",
            groupId = "${spring.kafka.consumer.group-id:aficio}"
    )
    public void consume(String payload) {
        try {
            ActivityEvent event = objectMapper.readValue(payload, ActivityEvent.class);
            log.info("Received activity event: type={}, userId={}, actorId={}", event.type(), event.userId(), event.actorId());

            Activity activity = activityRepository.save(
                    new Activity(event.userId(), event.actorId(), event.actorUsername(), event.type(), event.data())
            );
            log.info("Saved activity {} for user {}", activity.getId(), event.userId());
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize activity event, payload: {}, error: {}", payload, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process activity event, payload: {}, error: {}", payload, e.getMessage());
        }
    }
}
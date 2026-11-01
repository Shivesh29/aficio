package com.aficio.kafka;

public record SocialEvent(
        String type,
        String actorUsername,
        Long recipientId,
        String recipientUsername,
        String data
) {
}
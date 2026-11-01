package com.aficio.kafka;

public record ActivityEvent(
        String type,
        Long actorId,
        String actorUsername,
        Long userId,
        String data
) {
}
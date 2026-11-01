package com.aficio.activity;

import java.time.LocalDateTime;

public record ActivityResponse(
        Long id,
        String type,
        String actorUsername,
        String data,
        LocalDateTime createdAt
) {
    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getActorUsername(),
                activity.getData(),
                activity.getCreatedAt()
        );
    }
}


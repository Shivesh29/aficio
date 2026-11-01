package com.aficio.dto.social;

import com.aficio.dto.user.UserSummaryResponse;

import java.time.LocalDateTime;

public record FollowResponse(
        Long id,
        UserSummaryResponse follower,
        UserSummaryResponse following,
        String status,
        LocalDateTime createdAt
) {
    public FollowResponse(Long id, UserSummaryResponse follower, UserSummaryResponse following, LocalDateTime createdAt) {
        this(id, follower, following, "ACCEPTED", createdAt);
    }
}
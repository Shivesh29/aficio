package com.aficio.dto.social;

import com.aficio.dto.user.UserSummaryResponse;

import java.time.LocalDateTime;

public record ReviewCommentResponse(
        Long id,
        UserSummaryResponse user,
        String content,
        LocalDateTime createdAt
) {
}
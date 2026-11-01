package com.aficio.dto.message;

import com.aficio.dto.user.UserSummaryResponse;

import java.time.LocalDateTime;

public record DirectMessageResponse(
        Long id,
        UserSummaryResponse sender,
        UserSummaryResponse recipient,
        String content,
        LocalDateTime sentAt,
        boolean isRead,
        LocalDateTime readAt
) {
    public DirectMessageResponse(
            Long id,
            UserSummaryResponse sender,
            UserSummaryResponse recipient,
            String content,
            LocalDateTime sentAt
    ) {
        this(id, sender, recipient, content, sentAt, false, null);
    }
}

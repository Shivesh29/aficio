package com.aficio.dto.message;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        Long userId,
        String username,
        String lastMessage,
        LocalDateTime lastSentAt,
        boolean fromMe,
        long unreadCount
) {
    public ConversationSummaryResponse(
            Long userId,
            String username,
            String lastMessage,
            LocalDateTime lastSentAt,
            boolean fromMe
    ) {
        this(userId, username, lastMessage, lastSentAt, fromMe, 0L);
    }
}

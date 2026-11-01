package com.aficio.dto.track;

public record UserQuotaResponse(
        String clientId,
        boolean isOwner,
        boolean hasCustomKey,
        int dailyLimit,
        int usedToday,
        int remaining,
        boolean isExhausted,
        boolean unlimited
) {
}

package com.aficio.dto.track;

import java.util.List;

public record UserQuotaResponse(
        String clientId,
        boolean isOwner,
        boolean hasCustomKey,
        int dailyLimit,
        int usedToday,
        int remaining,
        boolean isExhausted,
        boolean unlimited,
        Integer systemSearchesToday,
        Integer systemSearchesLimit,
        Integer systemUnitsToday,
        Integer systemUnitsLimit,
        List<QuotaAuditEvent> recentQuotaEvents
) {
    public UserQuotaResponse(
            String clientId,
            boolean isOwner,
            boolean hasCustomKey,
            int dailyLimit,
            int usedToday,
            int remaining,
            boolean isExhausted,
            boolean unlimited
    ) {
        this(clientId, isOwner, hasCustomKey, dailyLimit, usedToday, remaining, isExhausted, unlimited, null, null, null, null, null);
    }
}

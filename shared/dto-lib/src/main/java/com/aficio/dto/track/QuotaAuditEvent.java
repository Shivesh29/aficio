package com.aficio.dto.track;

public record QuotaAuditEvent(
        String timestamp,
        String action,
        String target,
        String query,
        int units,
        boolean isSearchQuery,
        boolean success,
        String outcomeDetails
) {}


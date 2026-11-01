package com.aficio.dto.user;

public record UserSummaryResponse(
        Long id,
        String username,
        Boolean isPrivate
) {
    /** Backwards-compatible constructor for callers that don't know privacy status. */
    public UserSummaryResponse(Long id, String username) {
        this(id, username, null);
    }
}
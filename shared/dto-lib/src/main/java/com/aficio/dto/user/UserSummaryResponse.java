package com.aficio.dto.user;

public record UserSummaryResponse(
        Long id,
        String username,
        Boolean isPrivate,
        String listenerCode
) {
    public UserSummaryResponse(Long id, String username, Boolean isPrivate) {
        this(id, username, isPrivate, null);
    }

    public UserSummaryResponse(Long id, String username) {
        this(id, username, null, null);
    }
}

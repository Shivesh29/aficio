package com.aficio.dto.collection;

import com.aficio.dto.album.AlbumResponse;
import com.aficio.dto.user.UserSummaryResponse;

import java.time.LocalDateTime;

public record MusicListResponse(
        Long id,
        String title,
        String description,
        boolean isPublic,
        LocalDateTime createdAt,
        UserSummaryResponse owner
) {
}
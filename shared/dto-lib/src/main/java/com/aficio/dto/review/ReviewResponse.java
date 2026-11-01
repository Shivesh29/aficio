package com.aficio.dto.review;

import com.aficio.dto.album.AlbumResponse;
import com.aficio.dto.user.UserSummaryResponse;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        UserSummaryResponse user,
        AlbumResponse album,
        Double rating,
        String content,
        LocalDateTime createdAt,
        long likesCount,
        boolean likedByCurrentUser
) {
}
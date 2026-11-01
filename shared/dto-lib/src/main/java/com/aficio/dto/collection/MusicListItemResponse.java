package com.aficio.dto.collection;

import com.aficio.dto.album.AlbumResponse;

public record MusicListItemResponse(
        Long id,
        AlbumResponse album,
        Integer position
) {
}
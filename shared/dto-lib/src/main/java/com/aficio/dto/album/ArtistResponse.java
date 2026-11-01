package com.aficio.dto.album;

public record ArtistResponse(
        Long id,
        String name,
        String musicBrainzId
) {
}
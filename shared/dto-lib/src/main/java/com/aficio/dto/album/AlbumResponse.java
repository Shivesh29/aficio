package com.aficio.dto.album;

public record AlbumResponse(
        Long id,
        String title,
        String musicBrainzId,
        String coverArtUrl,
        ArtistResponse artist
) {
}
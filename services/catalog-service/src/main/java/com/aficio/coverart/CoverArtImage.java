package com.aficio.coverart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoverArtImage(
        boolean front,
        String image,
        CoverArtThumbnails thumbnails
) {
}
package com.aficio.coverart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoverArtThumbnails(
        String small,
        String large
) {
}
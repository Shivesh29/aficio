package com.aficio.coverart;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoverArtResponse(
        List<CoverArtImage> images
) {
}
package com.aficio.musicbrainz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzUrlResource(
        String id,
        String resource
) {
}

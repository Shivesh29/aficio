package com.aficio.musicbrainz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzArtist(
        String id,
        String name,
        String country,
        String disambiguation,
        String type
) {
}
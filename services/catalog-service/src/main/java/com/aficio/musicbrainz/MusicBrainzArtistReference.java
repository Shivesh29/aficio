package com.aficio.musicbrainz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzArtistReference(
        String id,
        String name
) {
}
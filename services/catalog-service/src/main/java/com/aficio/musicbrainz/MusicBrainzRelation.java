package com.aficio.musicbrainz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzRelation(
        String type,
        @JsonProperty("target-type") String targetType,
        MusicBrainzUrlResource url
) {
}

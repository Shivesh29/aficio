package com.aficio.musicbrainz;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzReleaseGroup(
        String id,
        String title,
        @JsonProperty("primary-type") String primaryType,
        @JsonProperty("secondary-types") List<String> secondaryTypes,
        @JsonProperty("first-release-date") String firstReleaseDate,
        String disambiguation,
        List<MusicBrainzRelation> relations,
        List<MusicBrainzRelease> releases
) {
    public MusicBrainzReleaseGroup(
            String id,
            String title,
            String primaryType,
            List<String> secondaryTypes,
            String firstReleaseDate,
            String disambiguation,
            List<MusicBrainzRelation> relations
    ) {
        this(id, title, primaryType, secondaryTypes, firstReleaseDate, disambiguation, relations, null);
    }
}


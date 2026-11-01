package com.aficio.musicbrainz;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzRecording(
        String id,
        String title,
        Integer length,
        Integer score,
        @JsonProperty("artist-credit") List<MusicBrainzArtistCredit> artistCredit,
        List<MusicBrainzRelease> releases,
        List<MusicBrainzRelation> relations
) {
}
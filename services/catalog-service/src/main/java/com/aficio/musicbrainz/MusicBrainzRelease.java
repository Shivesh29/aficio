package com.aficio.musicbrainz;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzRelease(
        String id,
        String title,
        String date,
        String country,
        String status,
        Integer score,
        @JsonProperty("track-count") Integer trackCount,
        @JsonProperty("artist-credit") List<MusicBrainzArtistCredit> artistCredit,
        @JsonProperty("release-group") MusicBrainzReleaseGroup releaseGroup
) {
}
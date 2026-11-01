package com.aficio.musicbrainz;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzReleaseDetails(
        String id,
        String title,
        @JsonProperty("artist-credit") List<MusicBrainzArtistCredit> artistCredit,
        List<MusicBrainzMedia> media,
        @JsonProperty("release-group") MusicBrainzReleaseGroup releaseGroup
) {
}
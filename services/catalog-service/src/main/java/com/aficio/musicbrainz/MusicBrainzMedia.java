package com.aficio.musicbrainz;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzMedia(
        Integer position,
        String format,
        List<MusicBrainzTrack> tracks
) {
}
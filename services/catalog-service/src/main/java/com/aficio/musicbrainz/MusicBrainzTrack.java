package com.aficio.musicbrainz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzTrack(
        String id,
        String number,
        String title,
        Integer length,
        MusicBrainzRecording recording
) {
}
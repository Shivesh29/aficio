package com.aficio.musicbrainz;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MusicBrainzRecordingSearchResponse(
        int count,
        List<MusicBrainzRecording> recordings
) {
}
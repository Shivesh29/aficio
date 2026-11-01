package com.aficio.dto.track;

public record TrackResponse(
        Long id,
        String musicBrainzId,
        String title,
        Integer position,
        Integer lengthMilliseconds
) {
}
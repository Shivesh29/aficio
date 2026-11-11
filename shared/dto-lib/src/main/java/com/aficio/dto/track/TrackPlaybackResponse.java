package com.aficio.dto.track;

public record TrackPlaybackResponse(
        Long trackId,
        String title,
        Integer position,
        Integer lengthMilliseconds,
        String artistName,
        String albumTitle,
        Long albumId,
        String coverArtUrl,
        String youtubeVideoId,
        String searchQuery,
        String source,
        Boolean newlyDiscovered,
        Integer dailyDiscoveriesUsed,
        Integer dailyDiscoveriesLimit,
        Integer remainingDiscoveries,
        Integer quotaUnitsUsedThisRequest,
        String quotaActionDetails,
        Integer totalSystemSearchesToday,
        Integer totalSystemUnitsToday
) {
    public TrackPlaybackResponse(
            Long trackId,
            String title,
            Integer position,
            Integer lengthMilliseconds,
            String artistName,
            String albumTitle,
            Long albumId,
            String coverArtUrl,
            String youtubeVideoId,
            String searchQuery,
            String source,
            Boolean newlyDiscovered,
            Integer dailyDiscoveriesUsed,
            Integer dailyDiscoveriesLimit,
            Integer remainingDiscoveries
    ) {
        this(
                trackId,
                title,
                position,
                lengthMilliseconds,
                artistName,
                albumTitle,
                albumId,
                coverArtUrl,
                youtubeVideoId,
                searchQuery,
                source,
                newlyDiscovered,
                dailyDiscoveriesUsed,
                dailyDiscoveriesLimit,
                remainingDiscoveries,
                0,
                null,
                null,
                null
        );
    }

    public TrackPlaybackResponse(
            Long trackId,
            String title,
            Integer position,
            Integer lengthMilliseconds,
            String artistName,
            String albumTitle,
            Long albumId,
            String coverArtUrl,
            String youtubeVideoId,
            String searchQuery,
            String source
    ) {
        this(trackId, title, position, lengthMilliseconds, artistName, albumTitle, albumId, coverArtUrl, youtubeVideoId, searchQuery, source, false, null, null, null, 0, null, null, null);
    }
}

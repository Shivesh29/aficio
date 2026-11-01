package com.aficio.feed;

import com.aficio.album.Album;
import com.aficio.musicbrainz.MusicBrainzRelease;

public record FeedReleaseResponse(
        Long id,
        String title,
        String musicBrainzId,
        String coverArtUrl,
        String artistName,
        String artistMusicBrainzId,
        String date,
        Integer trackCount,
        boolean inCatalog,
        String reason
) {
    public static FeedReleaseResponse fromAlbum(Album album, Integer trackCount, String reason) {
        String artistName = album.getArtist() != null ? album.getArtist().getName() : "Unknown Artist";
        String artistMbId = album.getArtist() != null ? album.getArtist().getMusicBrainzId() : null;
        return new FeedReleaseResponse(
                album.getId(),
                album.getTitle(),
                album.getMusicBrainzId(),
                album.getCoverArtUrl(),
                artistName,
                artistMbId,
                null,
                trackCount,
                true,
                reason
        );
    }

    public static FeedReleaseResponse fromAlbum(Album album, String reason) {
        return fromAlbum(album, null, reason);
    }

    public static FeedReleaseResponse fromMusicBrainz(
            MusicBrainzRelease rel,
            String artistName,
            String artistMbId,
            String reason
    ) {
        return new FeedReleaseResponse(
                null,
                rel.title(),
                rel.id(),
                null,
                artistName,
                artistMbId,
                rel.date(),
                rel.trackCount(),
                false,
                reason
        );
    }
}

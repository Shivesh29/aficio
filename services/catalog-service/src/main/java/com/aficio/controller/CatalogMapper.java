package com.aficio.controller;

import com.aficio.album.Album;
import com.aficio.album.AlbumRepository;
import com.aficio.artist.Artist;
import com.aficio.artist.ArtistRepository;
import com.aficio.dto.album.AlbumResponse;
import com.aficio.dto.album.ArtistResponse;
import com.aficio.track.Track;
import com.aficio.track.TrackRepository;
import org.springframework.stereotype.Component;

@Component
public class CatalogMapper {

    public static ArtistResponse toArtistResponse(Artist artist) {
        if (artist == null) return null;
        return new ArtistResponse(
                artist.getId(),
                artist.getName(),
                artist.getMusicBrainzId()
        );
    }

    public static AlbumResponse toAlbumResponse(Album album) {
        if (album == null) return null;
        return new AlbumResponse(
                album.getId(),
                album.getTitle(),
                album.getMusicBrainzId(),
                album.getCoverArtUrl(),
                toArtistResponse(album.getArtist())
        );
    }

    public static com.aficio.dto.track.TrackResponse toTrackResponse(Track track) {
        if (track == null) return null;
        return new com.aficio.dto.track.TrackResponse(
                track.getId(),
                track.getMusicBrainzId(),
                track.getTitle(),
                track.getPosition(),
                track.getLengthMilliseconds()
        );
    }
}
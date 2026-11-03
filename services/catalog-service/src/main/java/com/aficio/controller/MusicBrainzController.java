package com.aficio.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aficio.musicbrainz.MusicBrainzArtist;
import com.aficio.musicbrainz.MusicBrainzClient;
import com.aficio.musicbrainz.MusicBrainzRecordingSearchResponse;
import com.aficio.musicbrainz.MusicBrainzReleaseSearchResponse;
import com.aficio.musicbrainz.MusicBrainzSearchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
public class MusicBrainzController {

    private final MusicBrainzClient musicBrainzClient;
    private final com.aficio.catalog.CatalogService catalogService;
    private static final Logger log = LoggerFactory.getLogger(MusicBrainzController.class);

    public MusicBrainzController(MusicBrainzClient musicBrainzClient, com.aficio.catalog.CatalogService catalogService) {
        this.musicBrainzClient = musicBrainzClient;
        this.catalogService = catalogService;
    }

    @GetMapping("/musicbrainz/artists")
    public MusicBrainzSearchResponse searchArtists(
            @RequestParam String name
    ) {
        return catalogService.searchArtistsWithDataset(name);
    }

    @GetMapping("/musicbrainz/artists/{artistId}/releases")
    public MusicBrainzReleaseSearchResponse getArtistReleases(
            @PathVariable String artistId
    ) {
        return catalogService.getArtistReleasesWithDataset(artistId);
    }

    @GetMapping("/musicbrainz/artists/{artistId}")
    public MusicBrainzArtist getArtistDetails(
            @PathVariable String artistId
    ) {
        if (artistId != null && artistId.startsWith("yt-art-")) {
            try {
                String decodedName = new String(java.util.Base64.getUrlDecoder().decode(artistId.substring(7)), java.nio.charset.StandardCharsets.UTF_8);
                return new MusicBrainzArtist(artistId, decodedName, null, "Catalog Artist", "Person/Group");
            } catch (Exception ignored) {}
        }
        return musicBrainzClient.getArtistDetails(artistId);
    }

    @GetMapping("/musicbrainz/search")
    public Object unifiedSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "album") String type
    ) {
        return switch (type.toLowerCase()) {
            case "artist" -> catalogService.searchArtistsWithDataset(query);
            case "artist-releases" -> catalogService.getArtistReleasesWithDataset(query);
            case "album" -> catalogService.searchReleasesWithDataset(query);
            case "track" -> musicBrainzClient.searchRecordings(query);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported search type. Use artist, album, track, or artist-releases.");
        };
    }
}
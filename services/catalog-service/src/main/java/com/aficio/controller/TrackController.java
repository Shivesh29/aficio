package com.aficio.controller;

import com.aficio.dto.track.TrackResponse;
import com.aficio.dto.track.TrackPlaybackResponse;
import com.aficio.dto.track.UserQuotaResponse;
import com.aficio.dto.album.ArtistResponse;
import com.aficio.album.Album;
import com.aficio.album.AlbumRepository;
import com.aficio.artist.Artist;
import com.aficio.artist.ArtistRepository;
import com.aficio.coverart.CoverArtClient;
import com.aficio.musicbrainz.MusicBrainzClient;
import com.aficio.musicbrainz.MusicBrainzMedia;
import com.aficio.musicbrainz.MusicBrainzRecording;
import com.aficio.musicbrainz.MusicBrainzReleaseDetails;
import com.aficio.musicbrainz.MusicBrainzTrack;
import com.aficio.track.Track;
import com.aficio.track.TrackPlaybackService;
import com.aficio.track.TrackRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class TrackController {

    private final TrackRepository trackRepository;
    private final AlbumRepository albumRepository;
    private final MusicBrainzClient musicBrainzClient;
    private final TrackPlaybackService trackPlaybackService;
    private final com.aficio.catalog.CatalogService catalogService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TrackController.class);

    public TrackController(
            TrackRepository trackRepository,
            AlbumRepository albumRepository,
            MusicBrainzClient musicBrainzClient,
            TrackPlaybackService trackPlaybackService,
            com.aficio.catalog.CatalogService catalogService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        this.trackRepository = trackRepository;
        this.albumRepository = albumRepository;
        this.musicBrainzClient = musicBrainzClient;
        this.trackPlaybackService = trackPlaybackService;
        this.catalogService = catalogService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/tracks")
    public List<TrackResponse> getTracks(@RequestParam Long albumId) {
        if (catalogService != null) {
            Album album = albumRepository.findById(albumId).orElse(null);
            if (album != null) {
                catalogService.reconcileTracksWithDataset(album);
            }
        }
        return trackRepository.findByAlbumIdOrderByPosition(albumId)
                .stream()
                .map(CatalogMapper::toTrackResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/tracks/{id}/playback")
    public TrackPlaybackResponse getPlayback(
            @PathVariable Long id,
            @RequestParam(value = "apiKey", required = false) String queryApiKey,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String clientId = resolveClientId(request);
        String headerApiKey = request != null ? request.getHeader("X-YouTube-API-Key") : null;
        String customApiKey = (headerApiKey != null && !headerApiKey.isBlank())
                ? headerApiKey.trim()
                : (queryApiKey != null && !queryApiKey.isBlank() ? queryApiKey.trim() : null);

        if ((customApiKey == null || customApiKey.isBlank()) && clientId != null && clientId.startsWith("user:")) {
            customApiKey = trackPlaybackService.resolveUserCloudApiKey(clientId);
        }

        return trackPlaybackService.getPlayback(id, clientId, customApiKey);
    }

    @GetMapping("/catalog/quota")
    public UserQuotaResponse getUserQuota(
            @RequestParam(value = "apiKey", required = false) String queryApiKey,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String clientId = resolveClientId(request);
        String headerApiKey = request != null ? request.getHeader("X-YouTube-API-Key") : null;
        String customApiKey = (headerApiKey != null && !headerApiKey.isBlank())
                ? headerApiKey.trim()
                : (queryApiKey != null && !queryApiKey.isBlank() ? queryApiKey.trim() : null);

        if ((customApiKey == null || customApiKey.isBlank()) && clientId != null && clientId.startsWith("user:")) {
            customApiKey = trackPlaybackService.resolveUserCloudApiKey(clientId);
        }

        return trackPlaybackService.getUserQuota(clientId, customApiKey);
    }

    private String resolveClientId(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) return "default";

        // 0. Check explicit user identity headers
        String headerUsername = request.getHeader("X-User-Username");
        if (headerUsername == null || headerUsername.isBlank()) {
            headerUsername = request.getHeader("X-User-Identity");
        }
        if (headerUsername != null && !headerUsername.isBlank()) {
            return "user:" + headerUsername.trim();
        }

        // 1. Try extracting subject/username and email from Authorization Bearer JWT
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                try {
                    byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
                    com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(decoded);
                    String sub = payload.has("sub") ? payload.get("sub").asText() : "";
                    String email = payload.has("email") ? payload.get("email").asText() : "";
                    if (!email.isBlank()) {
                        return "user:" + sub + ":" + email;
                    }
                    if (!sub.isBlank()) {
                        return "user:" + sub;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. Check X-User-Email header
        String headerEmail = request.getHeader("X-User-Email");
        if (headerEmail != null && !headerEmail.isBlank()) {
            return "email:" + headerEmail.trim();
        }

        // 3. Try forwarded client IP
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }

        // 4. Fallback to remote address
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.isBlank()) {
            return "ip:" + remoteAddr;
        }

        return "default";
    }

    @PutMapping("/tracks/{id}/youtube-video-id")
    public org.springframework.http.ResponseEntity<Void> updateYoutubeVideoId(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body
    ) {
        if (body != null && body.containsKey("youtubeVideoId")) {
            trackPlaybackService.saveYoutubeVideoId(id, body.get("youtubeVideoId"));
        }
        return org.springframework.http.ResponseEntity.ok().build();
    }

    @PostMapping("/tracks/import")
    public List<TrackResponse> importTracks(
            @RequestParam Long albumId,
            @RequestParam @NotBlank(message = "Release ID is required") String releaseId
    ) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found"));

        MusicBrainzReleaseDetails release =
                musicBrainzClient.getReleaseDetails(releaseId);

        List<Track> importedTracks = new ArrayList<>();
        List<Track> existingTracks = trackRepository.findByAlbumIdOrderByPosition(albumId);
        int expectedTotalTracks = release.media().stream()
                .filter(m -> m.tracks() != null)
                .mapToInt(m -> m.tracks().size())
                .sum();

        if (!existingTracks.isEmpty() && existingTracks.size() == expectedTotalTracks) {
            return existingTracks.stream().map(CatalogMapper::toTrackResponse).collect(Collectors.toList());
        }

        if (!existingTracks.isEmpty()) {
            trackRepository.deleteAll(existingTracks);
        }

        int position = 1;

        for (MusicBrainzMedia media : release.media()) {
            if (media.tracks() == null) continue;
            for (MusicBrainzTrack musicBrainzTrack : media.tracks()) {
                MusicBrainzRecording recording = musicBrainzTrack.recording();

                String musicBrainzId = musicBrainzTrack.id();
                if (musicBrainzId == null || musicBrainzId.isBlank()) {
                    musicBrainzId = recording != null && recording.id() != null
                            ? recording.id()
                            : album.getMusicBrainzId() + "-trk-" + position;
                }

                String title = recording != null && recording.title() != null && !recording.title().isBlank()
                        ? recording.title()
                        : musicBrainzTrack.title();
                if (title == null || title.isBlank()) {
                    title = "Track " + position;
                }

                Integer length = recording != null && recording.length() != null
                        ? recording.length()
                        : musicBrainzTrack.length();

                Track track = new Track(
                        musicBrainzId,
                        title,
                        position,
                        length,
                        album
                );

                importedTracks.add(trackRepository.save(track));
                position++;
            }
        }

        return importedTracks.stream()
                .map(CatalogMapper::toTrackResponse)
                .collect(Collectors.toList());
    }
}
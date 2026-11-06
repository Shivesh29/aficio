package com.aficio.controller;

import com.aficio.dto.album.AlbumResponse;
import com.aficio.album.Album;
import com.aficio.album.AlbumRepository;
import com.aficio.artist.Artist;
import com.aficio.artist.ArtistRepository;
import com.aficio.catalog.CatalogService;
import com.aficio.coverart.CoverArtClient;
import com.aficio.musicbrainz.MusicBrainzClient;
import com.aficio.musicbrainz.MusicBrainzRelease;
import com.aficio.musicbrainz.MusicBrainzReleaseSearchResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class AlbumController {

    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final MusicBrainzClient musicBrainzClient;
    private final CoverArtClient coverArtClient;
    private final CatalogService catalogService;
    private final com.aficio.history.UserAlbumHistoryRepository userAlbumHistoryRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AlbumController.class);

    @org.springframework.beans.factory.annotation.Autowired
    public AlbumController(
            AlbumRepository albumRepository,
            ArtistRepository artistRepository,
            MusicBrainzClient musicBrainzClient,
            CoverArtClient coverArtClient,
            CatalogService catalogService,
            com.aficio.history.UserAlbumHistoryRepository userAlbumHistoryRepository,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        this.albumRepository = albumRepository;
        this.artistRepository = artistRepository;
        this.musicBrainzClient = musicBrainzClient;
        this.coverArtClient = coverArtClient;
        this.catalogService = catalogService;
        this.userAlbumHistoryRepository = userAlbumHistoryRepository;
        this.objectMapper = objectMapper;
    }

    public AlbumController(
            AlbumRepository albumRepository,
            ArtistRepository artistRepository,
            MusicBrainzClient musicBrainzClient,
            CoverArtClient coverArtClient,
            CatalogService catalogService
    ) {
        this(albumRepository, artistRepository, musicBrainzClient, coverArtClient, catalogService, null, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @GetMapping("/albums")
    public List<AlbumResponse> getAlbums(
            @RequestParam(value = "userOnly", required = false, defaultValue = "false") boolean userOnly,
            @RequestParam(value = "syncedOnly", required = false, defaultValue = "false") boolean syncedOnly,
            @RequestParam(value = "limit", required = false, defaultValue = "60") int limit,
            @RequestParam(value = "username", required = false) String paramUsername,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String username = paramUsername;
        if (username == null || username.isBlank()) {
            String clientId = resolveClientId(request);
            username = extractUsername(clientId);
        }

        int maxLimit = Math.min(Math.max(1, limit), 120);

        if (userOnly) {
            if (username == null || username.isBlank() || userAlbumHistoryRepository == null) {
                return Collections.emptyList();
            }
            List<com.aficio.history.UserAlbumHistory> historyList =
                    syncedOnly
                            ? userAlbumHistoryRepository.findByUsernameIgnoreCaseAndSyncedTrueOrderByPlayedAtDesc(username)
                            : userAlbumHistoryRepository.findByUsernameIgnoreCaseOrderByPlayedAtDesc(username);

            // Deduplicate by album ID, keeping most recently played, capped at maxLimit to prevent memory bloat/lag
            Set<Long> seenIds = new HashSet<>();
            List<AlbumResponse> results = new ArrayList<>();
            for (com.aficio.history.UserAlbumHistory history : historyList) {
                if (history.getAlbum() != null && seenIds.add(history.getAlbum().getId())) {
                    results.add(CatalogMapper.toAlbumResponse(history.getAlbum()));
                    if (results.size() >= maxLimit) {
                        break;
                    }
                }
            }
            return results;
        }

        List<Album> albums = albumRepository.findAll();
        if (syncedOnly) {
            albums = albums.stream()
                    .filter(a -> a.getYoutubePlaylistId() != null && !a.getYoutubePlaylistId().isBlank())
                    .collect(Collectors.toList());
        }
        return albums.stream()
                .limit(maxLimit)
                .map(CatalogMapper::toAlbumResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/albums/my")
    public List<AlbumResponse> getMyAlbums(
            @RequestParam(value = "syncedOnly", required = false, defaultValue = "false") boolean syncedOnly,
            @RequestParam(value = "limit", required = false, defaultValue = "60") int limit,
            @RequestParam(value = "username", required = false) String paramUsername,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        return getAlbums(true, syncedOnly, limit, paramUsername, request);
    }

    @GetMapping("/albums/feed")
    public List<com.aficio.feed.FeedReleaseResponse> getFeed(
            @RequestParam(value = "limit", required = false, defaultValue = "12") int limit,
            @RequestParam(value = "username", required = false) String paramUsername,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        String username = paramUsername;
        if (username == null || username.isBlank()) {
            String clientId = resolveClientId(request);
            username = extractUsername(clientId);
        }
        return catalogService.getPersonalizedFeed(username, limit);
    }

    private String resolveClientId(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) return "default";

        // 1. Check explicit user identity headers
        String headerUsername = request.getHeader("X-User-Username");
        if (headerUsername == null || headerUsername.isBlank()) {
            headerUsername = request.getHeader("X-User-Identity");
        }
        if (headerUsername != null && !headerUsername.isBlank()) {
            return "user:" + headerUsername.trim();
        }

        // 2. Try extracting subject/username from Authorization Bearer JWT
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

        // 3. Check X-User-Email header
        String headerEmail = request.getHeader("X-User-Email");
        if (headerEmail != null && !headerEmail.isBlank()) {
            return "email:" + headerEmail.trim();
        }
        return "default";
    }

    private String extractUsername(String clientId) {
        if (clientId == null) return null;
        if (clientId.startsWith("user:")) {
            String raw = clientId.substring(5);
            int colon = raw.indexOf(':');
            return (colon > 0 ? raw.substring(0, colon) : raw).trim();
        }
        if (clientId.startsWith("email:")) {
            String email = clientId.substring(6);
            int at = email.indexOf('@');
            return (at > 0 ? email.substring(0, at) : email).trim();
        }
        return null;
    }

    @GetMapping("/albums/{id}")
    public AlbumResponse getAlbumById(
            @PathVariable Long id,
            @RequestParam(value = "username", required = false) String paramUsername,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Album not found"
                ));
        if (album.getCoverArtUrl() == null && album.getMusicBrainzId() != null) {
            album = catalogService.resolveMissingCoverArt(album);
        }
        recordUserAlbumView(album, paramUsername, request);
        return CatalogMapper.toAlbumResponse(album);
    }

    private void recordUserAlbumView(Album album, String paramUsername, jakarta.servlet.http.HttpServletRequest request) {
        if (album == null || userAlbumHistoryRepository == null) return;
        String username = paramUsername;
        if ((username == null || username.isBlank()) && request != null) {
            String clientId = resolveClientId(request);
            username = extractUsername(clientId);
        }
        if (username == null || username.isBlank() || "default".equalsIgnoreCase(username)) return;

        try {
            final String finalUsername = username.trim().toLowerCase(java.util.Locale.ROOT);
            com.aficio.history.UserAlbumHistory history = userAlbumHistoryRepository
                    .findByUsernameIgnoreCaseAndAlbumId(finalUsername, album.getId())
                    .orElseGet(() -> new com.aficio.history.UserAlbumHistory(finalUsername, album, false));
            history.setPlayedAt(java.time.LocalDateTime.now());
            userAlbumHistoryRepository.save(history);
            log.info("Recorded album view for user [{}] album [{}] ({})", finalUsername, album.getId(), album.getTitle());
        } catch (Exception ex) {
            log.warn("Failed to record album view for user [{}] album [{}]: {}", username, album.getId(), ex.getMessage());
        }
    }

    @GetMapping("/artists/albums")
    public List<AlbumResponse> getAlbumsForArtist(@RequestParam Long artistId) {
        return albumRepository.findByArtistId(artistId)
                .stream()
                .map(CatalogMapper::toAlbumResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/musicbrainz/albums")
    public MusicBrainzReleaseSearchResponse searchAlbums(
            @RequestParam @NotBlank(message = "Title is required") String title
    ) {
        return catalogService.searchReleasesWithDataset(title);
    }

    @PostMapping("/albums")
    public AlbumResponse createAlbum(
            @RequestParam Long artistId,
            @Valid @RequestBody AlbumRequest request
    ) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found"));

        Album album = new Album(
                request.title(),
                request.musicBrainzId(),
                artist
        );

        return CatalogMapper.toAlbumResponse(albumRepository.save(album));
    }

    @PostMapping("/albums/import")
    public AlbumResponse importAlbum(
            @RequestParam Long artistId,
            @RequestBody MusicBrainzRelease release
    ) {
        Album existingAlbum = albumRepository
                .findFirstByMusicBrainzIdOrderByIdAsc(release.id())
                .orElse(null);

        String releaseGroupId = release.releaseGroup() != null ? release.releaseGroup().id() : null;

        if (existingAlbum != null) {
            if (existingAlbum.getCoverArtUrl() == null) {
                String coverArtUrl = coverArtClient.findFrontCover(release.id(), releaseGroupId);
                existingAlbum.setCoverArtUrl(coverArtUrl);
                return CatalogMapper.toAlbumResponse(albumRepository.save(existingAlbum));
            }
            return CatalogMapper.toAlbumResponse(existingAlbum);
        }

        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found"));

        Album album = new Album(
                release.title(),
                release.id(),
                artist
        );

        String coverArtUrl = coverArtClient.findFrontCover(release.id(), releaseGroupId);
        album.setCoverArtUrl(coverArtUrl);

        return CatalogMapper.toAlbumResponse(albumRepository.save(album));
    }

    public record AlbumRequest(
            @NotBlank(message = "Title is required")
            String title,
            String musicBrainzId
    ) {
    }
}
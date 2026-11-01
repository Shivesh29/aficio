package com.aficio.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aficio.dto.album.AlbumResponse;
import com.aficio.album.Album;
import com.aficio.catalog.CatalogService;
import com.aficio.history.UserAlbumHistory;
import com.aficio.history.UserAlbumHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {

    private final CatalogService catalogService;
    private final UserAlbumHistoryRepository userAlbumHistoryRepository;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(CatalogController.class);

    public CatalogController(
            CatalogService catalogService,
            UserAlbumHistoryRepository userAlbumHistoryRepository,
            ObjectMapper objectMapper
    ) {
        this.catalogService = catalogService;
        this.userAlbumHistoryRepository = userAlbumHistoryRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = {"/catalog/albums/{musicBrainzId}/sync", "/albums/{musicBrainzId}/sync"})
    public AlbumResponse syncAlbum(
            @PathVariable String musicBrainzId,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        Album album = catalogService.syncAlbum(musicBrainzId);
        recordUserSync(album, request);
        return CatalogMapper.toAlbumResponse(album);
    }

    private void recordUserSync(Album album, jakarta.servlet.http.HttpServletRequest request) {
        if (album == null || userAlbumHistoryRepository == null || request == null) return;
        String username = extractUsername(request);
        if (username == null || username.isBlank()) return;

        try {
            UserAlbumHistory history = userAlbumHistoryRepository
                    .findByUsernameIgnoreCaseAndAlbumId(username, album.getId())
                    .orElseGet(() -> new UserAlbumHistory(username, album, true));
            history.setSynced(true);
            history.setPlayedAt(java.time.LocalDateTime.now());
            userAlbumHistoryRepository.save(history);
            log.info("Recorded album sync for user {} on album {}", username, album.getTitle());
        } catch (Exception ex) {
            log.warn("Failed to record sync history for user {} on album {}: {}", username, album.getId(), ex.getMessage());
        }
    }

    private String extractUsername(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) return null;

        String headerUsername = request.getHeader("X-User-Username");
        if (headerUsername == null || headerUsername.isBlank()) {
            headerUsername = request.getHeader("X-User-Identity");
        }
        if (headerUsername != null && !headerUsername.isBlank()) {
            return headerUsername.trim();
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                try {
                    byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
                    com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(decoded);
                    if (payload.has("sub")) {
                        return payload.get("sub").asText();
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
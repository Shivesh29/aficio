package com.aficio.track;

import com.aficio.album.Album;
import com.aficio.album.AlbumRepository;
import com.aficio.dto.track.TrackPlaybackResponse;
import com.aficio.dto.track.UserQuotaResponse;
import com.aficio.musicbrainz.MusicBrainzClient;
import com.aficio.musicbrainz.MusicBrainzMedia;
import com.aficio.musicbrainz.MusicBrainzRecording;
import com.aficio.musicbrainz.MusicBrainzRelation;
import com.aficio.musicbrainz.MusicBrainzReleaseDetails;
import com.aficio.musicbrainz.MusicBrainzReleaseGroup;
import com.aficio.musicbrainz.MusicBrainzTrack;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TrackPlaybackService {

    public record PlaylistItem(String videoId, String title) {}
    public record CandidatePlaylist(String id, String title, String channelTitle, int initialScore) {}

    private static final Logger log = LoggerFactory.getLogger(TrackPlaybackService.class);

    private static final Pattern YOUTUBE_URL_PATTERN = Pattern.compile(
            "(?:https?:\\/\\/)?(?:www\\.|m\\.|music\\.)?(?:youtube\\.com\\/(?:watch\\?.*?v=|embed\\/|v\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})"
    );

    private static final Pattern YOUTUBE_PLAYLIST_PATTERN = Pattern.compile(
            "[?&]list=([a-zA-Z0-9_-]+)"
    );

    private final TrackRepository trackRepository;
    private final AlbumRepository albumRepository;
    private final MusicBrainzClient musicBrainzClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final HttpClient httpClient;
    private final com.aficio.history.UserAlbumHistoryRepository userAlbumHistoryRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Value("${aficio.youtube.api-key:}")
    private String youtubeApiKey;

    @Value("${aficio.youtube.album-sync-limit:4}")
    private int albumSyncLimit;

    @Value("${aficio.playback.enable-musicbrainz-ids:false}")
    private boolean enableMusicBrainzIds = false;

    public void setEnableMusicBrainzIds(boolean enableMusicBrainzIds) {
        this.enableMusicBrainzIds = enableMusicBrainzIds;
    }

    public boolean isEnableMusicBrainzIds() {
        return this.enableMusicBrainzIds;
    }

    /**
     * Daily album sync limit for personal YouTube API key holders.
     * Google Cloud YouTube Data API v3 grants 10,000 units/day.
     * Searching for an album playlist costs 100 units (search.list type=playlist),
     * allowing up to ~99 brand new album discoveries per day per personal key.
     */
    public static final int PERSONAL_KEY_ALBUM_SYNC_LIMIT = 99;

    @Value("${aficio.auth-service.url:http://auth-service:8081}")
    private String authServiceUrl;

    // Distinct album IDs synced via YouTube API per client identifier (in-memory fallback)
    private final ConcurrentHashMap<String, Set<Long>> clientSyncedAlbums = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public TrackPlaybackService(
            TrackRepository trackRepository,
            AlbumRepository albumRepository,
            MusicBrainzClient musicBrainzClient,
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            com.aficio.history.UserAlbumHistoryRepository userAlbumHistoryRepository,
            org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbcTemplateProvider
    ) {
        this.trackRepository = trackRepository;
        this.albumRepository = albumRepository;
        this.musicBrainzClient = musicBrainzClient;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        this.userAlbumHistoryRepository = userAlbumHistoryRepository;
        this.jdbcTemplate = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public TrackPlaybackService(
            TrackRepository trackRepository,
            AlbumRepository albumRepository,
            MusicBrainzClient musicBrainzClient,
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            com.aficio.history.UserAlbumHistoryRepository userAlbumHistoryRepository
    ) {
        this(trackRepository, albumRepository, musicBrainzClient, objectMapper, redisTemplateProvider, userAlbumHistoryRepository, null);
    }

    public TrackPlaybackService(
            TrackRepository trackRepository,
            AlbumRepository albumRepository,
            MusicBrainzClient musicBrainzClient,
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        this(trackRepository, albumRepository, musicBrainzClient, objectMapper, redisTemplateProvider, null, null);
    }

    public TrackPlaybackService(
            TrackRepository trackRepository,
            AlbumRepository albumRepository,
            MusicBrainzClient musicBrainzClient,
            ObjectMapper objectMapper
    ) {
        this(trackRepository, albumRepository, musicBrainzClient, objectMapper, null, null, null);
    }

    private String getRedisQuotaKey(String clientId) {
        String safeClient = (clientId != null && !clientId.isBlank()) ? clientId : "default";
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        return "aficio:quota:daily_discoveries:" + safeClient + ":" + today;
    }

    public String extractUsername(String clientId) {
        if (clientId == null || !clientId.startsWith("user:")) return null;
        String raw = clientId.substring(5);
        int colon = raw.indexOf(':');
        return colon > 0 ? raw.substring(0, colon) : raw;
    }

    public String resolveUserCloudApiKey(String clientId) {
        String username = extractUsername(clientId);
        if (username == null || username.isBlank()) return null;

        // 1. Check Redis cache first (instant hit)
        if (stringRedisTemplate != null) {
            try {
                String cached = stringRedisTemplate.opsForValue().get("aficio:user_key:" + username);
                if (cached != null && !cached.isBlank()) {
                    return cached.trim();
                }
            } catch (Exception ex) {
                log.warn("Redis error checking cached user API key for [{}]: {}", username, ex.getMessage());
            }
        }

        // 2. Fetch from auth-service internal endpoint
        try {
            String baseUrl = (authServiceUrl != null && !authServiceUrl.isBlank()) ? authServiceUrl.trim() : "http://auth-service:8081";
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String url = baseUrl + "/users/internal/" + username + "/youtube-api-key";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("apiKey") && !root.get("apiKey").isNull()) {
                    String key = root.get("apiKey").asText();
                    if (key != null && !key.isBlank()) {
                        key = key.trim();
                        if (stringRedisTemplate != null) {
                            try {
                                stringRedisTemplate.opsForValue().set("aficio:user_key:" + username, key, Duration.ofDays(7));
                            } catch (Exception ex) {
                                log.warn("Failed to cache resolved user API key in Redis: {}", ex.getMessage());
                            }
                        }
                        return key;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch cloud API key from auth-service for user [{}]: {}", username, ex.getMessage());
        }

        return null;
    }

    public boolean isOwnerAccount(String clientId) {
        if (clientId == null) return false;
        String lower = clientId.toLowerCase();
        return lower.contains("sharmashivesh29")
                || lower.contains("shivesh29")
                || lower.equals("user:shivesh")
                || lower.startsWith("user:shivesh:")
                || lower.endsWith(":shivesh");
    }

    public int getSyncedAlbumCount(String clientId) {
        if (stringRedisTemplate != null) {
            try {
                String key = getRedisQuotaKey(clientId);
                Long size = stringRedisTemplate.opsForSet().size(key);
                return size != null ? size.intValue() : 0;
            } catch (Exception ex) {
                log.warn("Redis error fetching synced album count for client [{}]: {}", clientId, ex.getMessage());
            }
        }
        return clientSyncedAlbums.getOrDefault(clientId, Collections.emptySet()).size();
    }

    public int getRemainingAlbumQuota(String clientId) {
        return getRemainingAlbumQuota(clientId, false);
    }

    public int getRemainingAlbumQuota(String clientId, boolean hasCustomKey) {
        if (isOwnerAccount(clientId)) {
            return Integer.MAX_VALUE;
        }
        int limit = hasCustomKey ? PERSONAL_KEY_ALBUM_SYNC_LIMIT : albumSyncLimit;
        return Math.max(0, limit - getSyncedAlbumCount(clientId));
    }

    public boolean canSyncAlbumViaYouTube(String clientId, Long albumId) {
        return canSyncAlbumViaYouTube(clientId, albumId, false);
    }

    public boolean canSyncAlbumViaYouTube(String clientId, Long albumId, boolean hasCustomKey) {
        // Owner/dev account (sharmashivesh29) has unlimited discovery using system API key
        if (isOwnerAccount(clientId)) {
            return true;
        }

        int limit = hasCustomKey ? PERSONAL_KEY_ALBUM_SYNC_LIMIT : albumSyncLimit;
        if (limit <= 0) return true; // unlimited if <= 0

        // If this album was already discovered today or previously by this client, replay is free!
        if (albumId != null) {
            if (stringRedisTemplate != null) {
                try {
                    String key = getRedisQuotaKey(clientId);
                    Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, albumId.toString());
                    if (Boolean.TRUE.equals(isMember)) {
                        return true;
                    }
                } catch (Exception ex) {
                    log.warn("Redis error checking album membership for [{}]: {}", albumId, ex.getMessage());
                }
            }
            Set<Long> synced = clientSyncedAlbums.getOrDefault(clientId, Collections.emptySet());
            if (synced.contains(albumId)) {
                return true;
            }
        }

        return getSyncedAlbumCount(clientId) < limit;
    }

    public void recordAlbumSynced(String clientId, Long albumId) {
        if (albumId == null) return;
        if (stringRedisTemplate != null) {
            try {
                String key = getRedisQuotaKey(clientId);
                stringRedisTemplate.opsForSet().add(key, albumId.toString());
                stringRedisTemplate.expire(key, Duration.ofDays(2));
            } catch (Exception ex) {
                log.warn("Redis error recording album sync for [{}] [{}]: {}", clientId, albumId, ex.getMessage());
            }
        }
        clientSyncedAlbums.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet()).add(albumId);
    }

    public UserQuotaResponse getUserQuota(String clientId, String customApiKey) {
        boolean isOwner = isOwnerAccount(clientId);
        boolean hasCustomKey = customApiKey != null && !customApiKey.isBlank();
        int used = getSyncedAlbumCount(clientId);
        int limit = isOwner ? -1 : (hasCustomKey ? PERSONAL_KEY_ALBUM_SYNC_LIMIT : albumSyncLimit);
        int remaining = isOwner ? Integer.MAX_VALUE : Math.max(0, limit - used);
        boolean unlimited = isOwner;
        boolean isExhausted = !unlimited && remaining <= 0;

        return new UserQuotaResponse(
                clientId,
                isOwner,
                hasCustomKey,
                limit,
                used,
                remaining,
                isExhausted,
                unlimited
        );
    }

    @Transactional
    public TrackPlaybackResponse getPlayback(Long trackId) {
        return getPlayback(trackId, "default", null);
    }

    @Transactional
    public TrackPlaybackResponse getPlayback(Long trackId, String clientId) {
        return getPlayback(trackId, clientId, null);
    }

    @Transactional
    public TrackPlaybackResponse getPlayback(Long trackId, String clientId, String customApiKey) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found with id: " + trackId));

        Album album = track.getAlbum();
        String artistName = (album != null && album.getArtist() != null) ? album.getArtist().getName() : "Unknown Artist";
        String albumTitle = (album != null) ? album.getTitle() : "Unknown Album";
        Long albumId = (album != null) ? album.getId() : null;
        String coverArtUrl = (album != null) ? album.getCoverArtUrl() : null;

        boolean hasCustomKey = customApiKey != null && !customApiKey.isBlank();
        String effectiveApiKey = hasCustomKey ? customApiKey.trim() : this.youtubeApiKey;
        int clientLimit = isOwnerAccount(clientId) ? -1 : (hasCustomKey ? PERSONAL_KEY_ALBUM_SYNC_LIMIT : albumSyncLimit);
        int clientRemaining = getRemainingAlbumQuota(clientId, hasCustomKey);

        // 1. Instant Cache: Check if already stored in database (0 quota units)
        if (track.getYoutubeVideoId() != null && !track.getYoutubeVideoId().isBlank()) {
            recordUserAlbumHistory(clientId, album, false);
            return new TrackPlaybackResponse(
                    track.getId(),
                    track.getTitle(),
                    track.getPosition(),
                    track.getLengthMilliseconds(),
                    artistName,
                    albumTitle,
                    albumId,
                    coverArtUrl,
                    track.getYoutubeVideoId(),
                    null,
                    "cached",
                    false,
                    getSyncedAlbumCount(clientId),
                    clientLimit,
                    clientRemaining
            );
        }

        String youtubeVideoId = null;
        String source = null;
        boolean newlyDiscovered = false;

        // 2. MusicBrainz Recording Relations Check (Disabled by default to strictly isolate 12M dataset)
        if (enableMusicBrainzIds && track.getMusicBrainzId() != null && !track.getMusicBrainzId().isBlank()) {
            try {
                youtubeVideoId = resolveFromMusicBrainz(track);
                if (youtubeVideoId != null) {
                    source = "musicbrainz";
                    track.setYoutubeVideoId(youtubeVideoId);
                    trackRepository.save(track);
                    log.info("Found YouTube video ID [{}] for track [{}] via MusicBrainz URL relation (0 units)", youtubeVideoId, track.getTitle());
                }
            } catch (Exception ex) {
                log.warn("Failed to check MusicBrainz relations for track [{}]: {}", track.getTitle(), ex.getMessage());
            }
        }

        // 3. LAION-DISCO-12M Catalog Lookup (0 quota units)
        if (youtubeVideoId == null) {
            try {
                youtubeVideoId = resolveFromYoutubeCatalog(track, album, artistName);
                if (youtubeVideoId != null) {
                    source = "youtube_catalog";
                }
            } catch (Exception ex) {
                log.warn("youtube_catalog check failed for [{}] by [{}]: {}", track.getTitle(), artistName, ex.getMessage());
            }
        }

        // 4. Album Playlist Resolution Engine: Batch resolve all album tracks via YouTube Data API
        if (youtubeVideoId == null && album != null) {
            try {
                newlyDiscovered = populateAlbumPlaylistTracks(album, clientId, effectiveApiKey, hasCustomKey);
                // Refresh local track instance after batch update
                Track refreshedTrack = trackRepository.findById(trackId).orElse(track);
                if (refreshedTrack.getYoutubeVideoId() != null && !refreshedTrack.getYoutubeVideoId().isBlank()) {
                    youtubeVideoId = refreshedTrack.getYoutubeVideoId();
                    source = hasCustomKey ? "custom_key_playlist" : "album_playlist";
                    track = refreshedTrack;
                }
            } catch (Exception ex) {
                log.warn("Album playlist resolution failed for album [{}]: {}", albumTitle, ex.getMessage());
            }
        }

        // 4. Targeted Single-Track Fallback: If still missing, try searching specifically for this track
        if (youtubeVideoId == null && effectiveApiKey != null && !effectiveApiKey.isBlank()) {
            if (canSyncAlbumViaYouTube(clientId, albumId, hasCustomKey)) {
                try {
                    String singleVid = searchYouTubeForSingleTrack(artistName, track.getTitle(), albumTitle, effectiveApiKey);
                    if (singleVid != null && !singleVid.isBlank()) {
                        track.setYoutubeVideoId(singleVid);
                        trackRepository.save(track);
                        youtubeVideoId = singleVid;
                        source = hasCustomKey ? "custom_key_track_search" : "track_search";
                        recordAlbumSynced(clientId, albumId);
                    }
                } catch (Exception ex) {
                    log.warn("Single-track fallback search failed for [{} - {}]: {}", artistName, track.getTitle(), ex.getMessage());
                }
            }
        }

        // 5. If still missing, determine if blocked by quota limit, missing API key, or truly not found (ZERO scraping requests)
        if (youtubeVideoId == null) {
            if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
                source = "api_key_missing";
            } else if (!canSyncAlbumViaYouTube(clientId, albumId, hasCustomKey)) {
                source = "limit_reached";
            } else {
                source = "unavailable";
            }
        }

        if (youtubeVideoId != null) {
            recordUserAlbumHistory(clientId, album, newlyDiscovered);
        }

        int currentUsed = getSyncedAlbumCount(clientId);
        int currentLimit = isOwnerAccount(clientId) ? -1 : (hasCustomKey ? PERSONAL_KEY_ALBUM_SYNC_LIMIT : albumSyncLimit);
        int currentRemaining = getRemainingAlbumQuota(clientId, hasCustomKey);

        return new TrackPlaybackResponse(
                track.getId(),
                track.getTitle(),
                track.getPosition(),
                track.getLengthMilliseconds(),
                artistName,
                albumTitle,
                albumId,
                coverArtUrl,
                youtubeVideoId,
                null,
                source != null ? source : "unavailable",
                newlyDiscovered,
                currentUsed,
                currentLimit,
                currentRemaining
        );
    }

    public void recordUserAlbumHistory(String clientId, Album album, boolean newlyDiscovered) {
        if (album == null || userAlbumHistoryRepository == null) return;
        String username = extractUsername(clientId);
        if (username == null || username.isBlank()) return;

        try {
            com.aficio.history.UserAlbumHistory history = userAlbumHistoryRepository
                    .findByUsernameIgnoreCaseAndAlbumId(username, album.getId())
                    .orElseGet(() -> new com.aficio.history.UserAlbumHistory(username, album, newlyDiscovered));
            history.setPlayedAt(java.time.LocalDateTime.now());
            if (newlyDiscovered) {
                history.setSynced(true);
            }
            userAlbumHistoryRepository.save(history);
        } catch (Exception ex) {
            log.warn("Failed to record album history for user [{}] album [{}]: {}", username, album.getId(), ex.getMessage());
        }
    }

    @Transactional
    public void saveYoutubeVideoId(Long trackId, String youtubeVideoId) {
        if (youtubeVideoId == null || youtubeVideoId.isBlank()) {
            return;
        }
        String cleanId = youtubeVideoId.trim();
        if (cleanId.length() > 50) {
            log.warn("Ignored suspicious YouTube video ID [{}] for track [{}]", cleanId, trackId);
            return;
        }

        trackRepository.findById(trackId).ifPresent(track -> {
            if (track.getYoutubeVideoId() == null || track.getYoutubeVideoId().isBlank()) {
                track.setYoutubeVideoId(cleanId);
                trackRepository.save(track);
                log.info("Crowdsourced cache hit: Saved YouTube video ID [{}] for track [{}] ({})", cleanId, track.getId(), track.getTitle());
            }
        });
    }

    @Transactional
    public void invalidateRestrictedYoutubeVideoId(Long trackId) {
        if (trackId == null) return;
        trackRepository.findById(trackId).ifPresent(track -> {
            String badId = track.getYoutubeVideoId();
            if (badId != null && !badId.isBlank()) {
                track.setYoutubeVideoId(null);
                trackRepository.save(track);
                log.info("Invalidated restricted YouTube video ID [{}] for track [{}] ({})", badId, track.getId(), track.getTitle());
            }
        });
    }

    /**
     * Resolves YouTube video ID from MusicBrainz recording URL relations.
     * If the track's musicBrainzId was stored as a track MBID rather than a recording MBID,
     * resolves the true recording MBID from the release details and self-heals the track entity.
     */
    private String resolveFromMusicBrainz(Track track) {
        if (track == null) return null;
        String mbid = track.getMusicBrainzId();
        String videoId = tryGetVideoIdFromRecording(mbid);
        if (videoId != null) {
            return videoId;
        }

        // Fallback: look up the true recording MBID from the album's release details
        Album album = track.getAlbum();
        if (album != null && album.getMusicBrainzId() != null && !album.getMusicBrainzId().isBlank()) {
            try {
                MusicBrainzReleaseDetails releaseDetails = musicBrainzClient.getReleaseDetails(album.getMusicBrainzId());
                if (releaseDetails != null && releaseDetails.media() != null) {
                    int targetPos = track.getPosition() != null ? track.getPosition() : -1;
                    int currentPos = 1;
                    for (MusicBrainzMedia media : releaseDetails.media()) {
                        if (media.tracks() == null) continue;
                        for (MusicBrainzTrack mbTrack : media.tracks()) {
                            boolean matchPos = targetPos == currentPos;
                            boolean matchTitle = mbTrack.title() != null && mbTrack.title().equalsIgnoreCase(track.getTitle());
                            boolean matchId = mbTrack.id() != null && mbTrack.id().equals(track.getMusicBrainzId());

                            if (matchPos || matchTitle || matchId) {
                                if (mbTrack.recording() != null && mbTrack.recording().id() != null) {
                                    String actualRecordingId = mbTrack.recording().id();
                                    track.setMusicBrainzId(actualRecordingId);
                                    videoId = tryGetVideoIdFromRecording(actualRecordingId);
                                    if (videoId != null) {
                                        return videoId;
                                    }
                                }
                                break;
                            }
                            currentPos++;
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Could not resolve recording from release details: {}", ex.getMessage());
            }
        }
        return null;
    }

    private String tryGetVideoIdFromRecording(String recordingMbid) {
        if (recordingMbid == null || recordingMbid.isBlank()) return null;
        try {
            MusicBrainzRecording recording = musicBrainzClient.getRecordingDetails(recordingMbid);
            if (recording != null && recording.relations() != null) {
                for (MusicBrainzRelation relation : recording.relations()) {
                    if (relation.url() != null && relation.url().resource() != null) {
                        Matcher matcher = YOUTUBE_URL_PATTERN.matcher(relation.url().resource());
                        if (matcher.find()) {
                            return matcher.group(1);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Recording lookup failed for MBID [{}]: {}", recordingMbid, e.getMessage());
        }
        return null;
    }

    /**
     * Resolves YouTube video ID from the local pre-indexed LAION-DISCO-12M dataset
     * in PostgreSQL (0 quota units, ~0.5ms indexed query).
     */
    public String resolveFromYoutubeCatalog(Track track, Album album, String artistName) {
        if (jdbcTemplate == null || track == null) return null;

        try {
            String title = track.getTitle() != null ? track.getTitle().trim() : "";
            if (title.isBlank() || artistName == null || artistName.isBlank()) return null;

            String albumTitle = (album != null && album.getTitle() != null) ? album.getTitle().trim() : "";

            // Query youtube_catalog: Match on artist and title, preferring matching album name
            // Uses B-tree index idx_yt_cat_artist_title (LOWER(artist), LOWER(title)) in ~0.5ms!
            String sql = """
                SELECT song_id FROM youtube_catalog
                WHERE LOWER(artist) = LOWER(?)
                  AND LOWER(title) = LOWER(?)
                ORDER BY (CASE WHEN ? != '' AND LOWER(album) = LOWER(?) THEN 0 ELSE 1 END), duration DESC
                LIMIT 1
            """;

            List<String> results = jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> rs.getString("song_id"),
                    artistName, title, albumTitle, albumTitle
            );

            // If not found with exact title, try normalized/cleaned title if different
            // e.g. "Song Name (2011 Remaster)" -> "Song Name"
            if (results.isEmpty()) {
                String normalized = normalizeTitleForCatalog(title);
                if (!normalized.equalsIgnoreCase(title) && !normalized.isBlank()) {
                    results = jdbcTemplate.query(
                            sql,
                            (rs, rowNum) -> rs.getString("song_id"),
                            artistName, normalized, albumTitle, albumTitle
                    );
                }
            }

            // If still not found, try prefix and compound search e.g. "If You Must" -> "If You Must (Demo)"
            // or "Fairies Wear Boots" -> "Jack the Stripper / Fairies Wear Boots"
            if (results.isEmpty()) {
                String cleanBase = normalizeTitleForCatalog(title);
                if (!cleanBase.isBlank()) {
                    String patternSql = """
                        SELECT song_id FROM youtube_catalog
                        WHERE LOWER(artist) = LOWER(?)
                          AND (
                            LOWER(title) LIKE LOWER(?) || ' (%'
                            OR LOWER(title) LIKE LOWER(?) || ' - %'
                            OR LOWER(title) LIKE '% / ' || LOWER(?) || '%'
                            OR LOWER(title) LIKE '%' || LOWER(?) || ' / %'
                          )
                        ORDER BY (CASE WHEN ? != '' AND LOWER(album) = LOWER(?) THEN 0 ELSE 1 END), duration DESC
                        LIMIT 1
                    """;
                    results = jdbcTemplate.query(
                            patternSql,
                            (rs, rowNum) -> rs.getString("song_id"),
                            artistName, cleanBase, cleanBase, cleanBase, cleanBase, albumTitle, albumTitle
                    );
                }
            }

            if (!results.isEmpty() && results.get(0) != null && !results.get(0).isBlank()) {
                String videoId = results.get(0).trim();
                track.setYoutubeVideoId(videoId);
                trackRepository.save(track);
                log.info("Found YouTube video ID [{}] for track [{}] by [{}] via LAION-DISCO-12M catalog (0 quota units)",
                        videoId, track.getTitle(), artistName);

                // Opportunistically populate sibling tracks in this album from youtube_catalog
                if (album != null && album.getId() != null) {
                    populateSiblingTracksFromCatalog(album, artistName);
                }

                return videoId;
            }
        } catch (Exception ex) {
            log.warn("Failed to query youtube_catalog for track [{}]: {}", track.getTitle(), ex.getMessage());
        }

        return null;
    }

    /**
     * Batch populates all missing sibling tracks in an album from youtube_catalog (0 quota units).
     */
    public void populateSiblingTracksFromCatalog(Album album, String artistName) {
        if (jdbcTemplate == null || album == null || album.getId() == null) return;
        try {
            List<Track> albumTracks = trackRepository.findByAlbumIdOrderByPosition(album.getId());
            List<Track> missingTracks = albumTracks.stream()
                    .filter(t -> t.getYoutubeVideoId() == null || t.getYoutubeVideoId().isBlank())
                    .toList();

            if (missingTracks.isEmpty()) return;

            String albumTitle = album.getTitle() != null ? album.getTitle().trim() : "";

            int populatedCount = 0;
            for (Track t : missingTracks) {
                String title = t.getTitle() != null ? t.getTitle().trim() : "";
                if (title.isBlank()) continue;

                String sql = """
                    SELECT song_id FROM youtube_catalog
                    WHERE LOWER(artist) = LOWER(?)
                      AND LOWER(title) = LOWER(?)
                    ORDER BY (CASE WHEN ? != '' AND LOWER(album) = LOWER(?) THEN 0 ELSE 1 END), duration DESC
                    LIMIT 1
                """;

                List<String> results = jdbcTemplate.query(
                        sql,
                        (rs, rowNum) -> rs.getString("song_id"),
                        artistName, title, albumTitle, albumTitle
                );

                if (results.isEmpty()) {
                    String normalized = normalizeTitleForCatalog(title);
                    if (!normalized.equalsIgnoreCase(title) && !normalized.isBlank()) {
                        results = jdbcTemplate.query(
                                sql,
                                (rs, rowNum) -> rs.getString("song_id"),
                                artistName, normalized, albumTitle, albumTitle
                        );
                    }
                }

                if (results.isEmpty()) {
                    String cleanBase = normalizeTitleForCatalog(title);
                    if (!cleanBase.isBlank()) {
                        String patternSql = """
                            SELECT song_id FROM youtube_catalog
                            WHERE LOWER(artist) = LOWER(?)
                              AND (
                                LOWER(title) LIKE LOWER(?) || ' (%'
                                OR LOWER(title) LIKE LOWER(?) || ' - %'
                                OR LOWER(title) LIKE '% / ' || LOWER(?) || '%'
                                OR LOWER(title) LIKE '%' || LOWER(?) || ' / %'
                              )
                            ORDER BY (CASE WHEN ? != '' AND LOWER(album) = LOWER(?) THEN 0 ELSE 1 END), duration DESC
                            LIMIT 1
                        """;
                        results = jdbcTemplate.query(
                                patternSql,
                                (rs, rowNum) -> rs.getString("song_id"),
                                artistName, cleanBase, cleanBase, cleanBase, cleanBase, albumTitle, albumTitle
                        );
                    }
                }

                if (!results.isEmpty() && results.get(0) != null && !results.get(0).isBlank()) {
                    t.setYoutubeVideoId(results.get(0).trim());
                    trackRepository.save(t);
                    populatedCount++;
                }
            }

            if (populatedCount > 0) {
                log.info("Batch populated {}/{} missing sibling tracks for album [{}] from youtube_catalog (0 units)",
                        populatedCount, missingTracks.size(), album.getTitle());
            }
        } catch (Exception ex) {
            log.warn("Failed to populate sibling tracks from youtube_catalog for album [{}]: {}", album.getTitle(), ex.getMessage());
        }
    }

    private String normalizeTitleForCatalog(String title) {
        if (title == null) return "";
        String s = Normalizer.normalize(title, Normalizer.Form.NFD);
        s = s.replaceAll("\\p{M}", "");
        s = s.replaceAll("[‐‑–—]", "-");
        s = s.replaceAll("['’`]", "'");
        // Strip parenthetical/bracketed notes like (2011 Remaster), [Deluxe Edition], (feat. XYZ)
        s = s.replaceAll("(?i)\\s*[\\[\\(].*?(remaster|deluxe|bonus|version|edition|mono|stereo|anniversary|feat\\.|ft\\.).*?[\\]\\)]", "");
        // Strip trailing hyphen notes like " - Remastered 2011"
        s = s.replaceAll("(?i)\\s*-\\s*(remaster(ed)?|deluxe|bonus track).*$", "");
        return s.trim();
    }

    /**
     * Resolves the official YouTube playlist for an album and batch-populates all missing track video IDs.
     */
    private boolean populateAlbumPlaylistTracks(Album album, String clientId, String effectiveApiKey, boolean isCustomKey) {
        if (album == null || album.getId() == null) return false;

        List<Track> albumTracks = trackRepository.findByAlbumIdOrderByPosition(album.getId());
        if (albumTracks.isEmpty()) return false;

        boolean hasMissing = albumTracks.stream().anyMatch(t -> t.getYoutubeVideoId() == null || t.getYoutubeVideoId().isBlank());
        if (!hasMissing) {
            return false;
        }

        String artistName = (album.getArtist() != null) ? album.getArtist().getName() : "";
        List<PlaylistItem> workingPlaylistItems = null;
        String workingPlaylistId = null;
        boolean newlyDiscovered = false;

        // 1. If album already has a saved youtubePlaylistId, verify if it yields playable and matching tracks
        if (album.getYoutubePlaylistId() != null && !album.getYoutubePlaylistId().isBlank()) {
            List<PlaylistItem> items = fetchPlaylistItems(album.getYoutubePlaylistId(), effectiveApiKey);
            if (items != null && !items.isEmpty()) {
                int matchScore = calculatePlaylistMatchScore(albumTracks, items, artistName, album.getTitle());
                double matchRatio = (double) matchScore / albumTracks.size();
                if (matchRatio >= 0.90 || (albumTracks.size() <= 2 && matchRatio >= 0.50)) {
                    workingPlaylistId = album.getYoutubePlaylistId();
                    workingPlaylistItems = items;
                    log.info("Stored playlist [{}] verified for album [{}] with {}/{} track title matches",
                            workingPlaylistId, album.getTitle(), matchScore, albumTracks.size());
                } else {
                    log.warn("Stored playlist ID [{}] for album [{}] had low title match ratio ({}/{} tracks matched). Clearing stored playlist to search for complete release.",
                            album.getYoutubePlaylistId(), album.getTitle(), matchScore, albumTracks.size());
                    album.setYoutubePlaylistId(null);
                    albumRepository.save(album);
                }
            } else {
                log.warn("Stored playlist ID [{}] for album [{}] returned 404 or empty. Clearing and falling back to discovery.",
                        album.getYoutubePlaylistId(), album.getTitle());
                album.setYoutubePlaylistId(null);
                albumRepository.save(album);
            }
        }

        // 2. Try direct playlist ID from MusicBrainz release-group relations (Disabled by default to strictly isolate 12M dataset)
        if (enableMusicBrainzIds && workingPlaylistId == null) {
            String mbPlaylistId = resolveMusicBrainzPlaylistId(album);
            if (mbPlaylistId != null && !mbPlaylistId.isBlank()) {
                List<PlaylistItem> candidateItems = fetchPlaylistItems(mbPlaylistId, effectiveApiKey);
                if (candidateItems != null && !candidateItems.isEmpty()) {
                    int matchScore = calculatePlaylistMatchScore(albumTracks, candidateItems, artistName, album.getTitle());
                    double matchRatio = (double) matchScore / albumTracks.size();
                    if (matchRatio >= 0.60 || (albumTracks.size() <= 2 && matchRatio >= 0.50)) {
                        workingPlaylistId = mbPlaylistId;
                        workingPlaylistItems = candidateItems;
                        album.setYoutubePlaylistId(workingPlaylistId);
                        albumRepository.save(album);
                        log.info("MusicBrainz playlist [{}] verified for album [{}] with {}/{} track title matches (0 search units)",
                                workingPlaylistId, album.getTitle(), matchScore, albumTracks.size());
                    } else {
                        log.warn("MusicBrainz playlist ID [{}] for [{}] failed track title validation ({}/{} matched). Falling back to YouTube search.",
                                mbPlaylistId, album.getTitle(), matchScore, albumTracks.size());
                    }
                } else {
                    log.warn("MusicBrainz playlist ID [{}] for [{}] returned 404 or no tracks. Falling back to YouTube API search.",
                            mbPlaylistId, album.getTitle());
                }
            }
        }

        // 3. Official YouTube Data API Multi-Candidate Search (100 units)
        if (workingPlaylistId == null) {
            List<CandidatePlaylist> candidates = searchYouTubeForCandidatePlaylists(artistName, album.getTitle(), clientId, album.getId(), effectiveApiKey, isCustomKey);
            CandidatePlaylist bestCand = null;
            List<PlaylistItem> bestItems = null;
            int bestScore = 0;

            int testedCount = 0;
            for (CandidatePlaylist cand : candidates) {
                if (testedCount >= 4) break; // Check at most top 4 candidates to conserve quota
                testedCount++;

                List<PlaylistItem> candidateItems = fetchPlaylistItems(cand.id(), effectiveApiKey);
                if (candidateItems != null && !candidateItems.isEmpty()) {
                    int score = calculatePlaylistMatchScore(albumTracks, candidateItems, artistName, album.getTitle());
                    log.info("Candidate playlist [{}] ({}) by [{}] matched {}/{} tracks (initial score: {})",
                            cand.title(), cand.id(), cand.channelTitle(), score, albumTracks.size(), cand.initialScore());

                    if (score == albumTracks.size() && cand.initialScore() >= 0) {
                        bestCand = cand;
                        bestItems = candidateItems;
                        bestScore = score;
                        break;
                    } else if (score > bestScore) {
                        bestCand = cand;
                        bestItems = candidateItems;
                        bestScore = score;
                    }
                }
            }

            if (bestCand != null) {
                double ratio = (double) bestScore / albumTracks.size();
                if (ratio >= 0.70 || (albumTracks.size() <= 2 && ratio >= 0.50)) {
                    workingPlaylistId = bestCand.id();
                    workingPlaylistItems = bestItems;
                    album.setYoutubePlaylistId(workingPlaylistId);
                    albumRepository.save(album);
                    newlyDiscovered = true;
                    recordAlbumSynced(clientId, album.getId());
                    log.info("[QUOTA-USAGE] Selected best verified playlist [{}] ({}) by [{}] for album [{}] - {}/{} tracks matched (Key: {})",
                            bestCand.title(), bestCand.id(), bestCand.channelTitle(), album.getTitle(), bestScore, albumTracks.size(), isCustomKey ? "personal" : "system");
                }
            }
        }

        // 4. Batch populate album tracks with verified video IDs (NEVER blind index zipping)
        if (workingPlaylistItems != null && !workingPlaylistItems.isEmpty()) {
            Map<Long, String> trackToVideoMap = new HashMap<>();
            Set<String> usedVideoIds = new HashSet<>();

            // Pass 1: Strict sequential matching (if track[i] matches playlistItem[i])
            for (int i = 0; i < albumTracks.size() && i < workingPlaylistItems.size(); i++) {
                Track t = albumTracks.get(i);
                PlaylistItem item = workingPlaylistItems.get(i);
                if (isTrackTitleMatch(t.getTitle(), item.title(), artistName, album.getTitle())) {
                    trackToVideoMap.put(t.getId(), item.videoId());
                    usedVideoIds.add(item.videoId());
                }
            }

            // Pass 2: Search remaining playlist items for any unmatched tracks
            for (Track t : albumTracks) {
                if (trackToVideoMap.containsKey(t.getId())) continue;
                for (PlaylistItem item : workingPlaylistItems) {
                    if (!usedVideoIds.contains(item.videoId()) && isTrackTitleMatch(t.getTitle(), item.title(), artistName, album.getTitle())) {
                        trackToVideoMap.put(t.getId(), item.videoId());
                        usedVideoIds.add(item.videoId());
                        break;
                    }
                }
            }

            boolean changed = false;
            for (Track t : albumTracks) {
                String matchedVid = trackToVideoMap.get(t.getId());
                if (matchedVid != null && !matchedVid.equals(t.getYoutubeVideoId())) {
                    t.setYoutubeVideoId(matchedVid);
                    changed = true;
                }
            }
            if (changed) {
                trackRepository.saveAll(albumTracks);
                log.info("[QUOTA-USAGE] Batch-saved title-verified YouTube video IDs for album [{}] ({}) via playlist [{}] ({} tracks populated | Key: {})",
                        album.getTitle(), album.getId(), workingPlaylistId, trackToVideoMap.size(),
                        isCustomKey ? "personal_key" : "system_key");
            }
        }

        return newlyDiscovered;
    }

    /**
     * Resolves direct YouTube playlist relation from MusicBrainz release-group relations (0 search units).
     */
    private String resolveMusicBrainzPlaylistId(Album album) {
        if (album == null) return null;

        if (album.getMusicBrainzId() != null && !album.getMusicBrainzId().isBlank()) {
            try {
                MusicBrainzReleaseDetails releaseDetails = musicBrainzClient.getReleaseDetails(album.getMusicBrainzId());
                if (releaseDetails != null && releaseDetails.releaseGroup() != null && releaseDetails.releaseGroup().id() != null) {
                    String rgId = releaseDetails.releaseGroup().id();
                    MusicBrainzReleaseGroup rg = musicBrainzClient.getReleaseGroupDetails(rgId);
                    if (rg != null && rg.relations() != null) {
                        for (MusicBrainzRelation rel : rg.relations()) {
                            if (rel.url() != null && rel.url().resource() != null) {
                                String res = rel.url().resource();

                                // Check direct YouTube playlist relation
                                Matcher plMatcher = YOUTUBE_PLAYLIST_PATTERN.matcher(res);
                                if (plMatcher.find()) {
                                    String pid = plMatcher.group(1);
                                    log.info("Found YouTube playlist ID [{}] from MusicBrainz release-group relation for [{}] (0 search units)", pid, album.getTitle());
                                    return pid;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("MusicBrainz release-group relations check failed for album [{}]: {}", album.getTitle(), ex.getMessage());
            }
        }
        return null;
    }

    /**
     * Searches YouTube for candidate album playlists using official YouTube Data API v3 search.list (type=playlist) (100 units).
     * Retrieves up to 8 candidate playlists and scores them to prioritize official Topic releases and title matches.
     */
    private List<CandidatePlaylist> searchYouTubeForCandidatePlaylists(String artistName, String albumTitle, String clientId, Long albumId, String effectiveApiKey, boolean isCustomKey) {
        List<CandidatePlaylist> candidates = new ArrayList<>();
        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            log.warn("YouTube API key is not configured. Skipping YouTube playlist search (0 quota units).");
            return candidates;
        }

        if (!canSyncAlbumViaYouTube(clientId, albumId, isCustomKey)) {
            int limit = isCustomKey ? PERSONAL_KEY_ALBUM_SYNC_LIMIT : albumSyncLimit;
            log.warn("[QUOTA-LIMIT] Client [{}] has reached YouTube API album discovery limit ({}/{} albums | Key: {}). Skipping search for [{}] to protect API quota.",
                    clientId, getSyncedAlbumCount(clientId), limit, isCustomKey ? "personal" : "system", albumTitle);
            return candidates;
        }

        String query = (artistName + " " + albumTitle + " album").trim();
        try {
            int currentCount = getSyncedAlbumCount(clientId);
            boolean isOwner = isOwnerAccount(clientId);
            int effectiveLimit = isCustomKey ? PERSONAL_KEY_ALBUM_SYNC_LIMIT : albumSyncLimit;
            if (isCustomKey) {
                log.info("[QUOTA-USAGE] Client [{}] querying YouTube Data API search.list (type=playlist) using PERSONAL API key for [{}] [Cost: 100 quota units | Album {}/{} | 10k Quota]",
                        clientId, query, currentCount + 1, effectiveLimit);
            } else {
                log.info("[QUOTA-USAGE] Client [{}] querying YouTube Data API search.list (type=playlist) for [{}] [Cost: 100 quota units | Album {}/{} | Owner: {}]",
                        clientId, query, currentCount + 1, isOwner ? "UNLIMITED" : effectiveLimit, isOwner);
            }

            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String apiUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=playlist&maxResults=8&q="
                    + encodedQuery + "&key=" + effectiveApiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode items = root.path("items");
                if (items.isArray() && !items.isEmpty()) {
                    for (JsonNode item : items) {
                        String pid = item.path("id").path("playlistId").asText();
                        String pTitle = item.path("snippet").path("title").asText();
                        String channelTitle = item.path("snippet").path("channelTitle").asText();

                        if (pid.isBlank()) continue;

                        int score = scoreCandidatePlaylist(pid, pTitle, channelTitle, artistName, albumTitle);
                        candidates.add(new CandidatePlaylist(pid, pTitle, channelTitle, score));
                    }

                    // Sort descending by initial score
                    candidates.sort(Comparator.comparingInt(CandidatePlaylist::initialScore).reversed());
                }
            } else {
                log.warn("YouTube Data API search returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.warn("YouTube Data API playlist search error for [{}]: {}", query, ex.getMessage());
        }

        return candidates;
    }

    /**
     * Extracts video items (ID + Title) from a YouTube playlist using official YouTube Data API playlistItems.list (1 unit).
     */
    private List<PlaylistItem> fetchPlaylistItems(String playlistId, String effectiveApiKey) {
        List<PlaylistItem> itemsList = new ArrayList<>();
        if (effectiveApiKey == null || effectiveApiKey.isBlank() || playlistId == null || playlistId.isBlank()) {
            return itemsList;
        }

        try {
            log.info("[QUOTA-USAGE] Querying YouTube Data API playlistItems.list for playlist [{}] [Cost: 1 quota unit]", playlistId);
            String apiUrl = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&maxResults=50&playlistId="
                    + URLEncoder.encode(playlistId, StandardCharsets.UTF_8)
                    + "&key=" + effectiveApiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode items = root.path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String vid = item.path("contentDetails").path("videoId").asText();
                        String title = item.path("snippet").path("title").asText();
                        if (!vid.isBlank() && itemsList.stream().noneMatch(p -> p.videoId().equals(vid))) {
                            itemsList.add(new PlaylistItem(vid, title));
                        }
                    }
                }
                if (!itemsList.isEmpty()) {
                    log.info("[QUOTA-USAGE] Fetched {} track items from playlist [{}] via YouTube Data API (1 quota unit)", itemsList.size(), playlistId);
                    return itemsList;
                }
            } else {
                log.warn("YouTube Data API playlistItems returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.warn("YouTube Data API playlistItems fetch failed for [{}]: {}", playlistId, ex.getMessage());
        }

        return itemsList;
    }

    /**
     * Searches YouTube Data API specifically for an individual missing track with title verification.
     */
    private String searchYouTubeForSingleTrack(String artistName, String trackTitle, String albumTitle, String effectiveApiKey) {
        if (effectiveApiKey == null || effectiveApiKey.isBlank() || trackTitle == null || trackTitle.isBlank()) {
            return null;
        }
        try {
            String q = (artistName + " " + trackTitle + " " + (albumTitle != null ? albumTitle : "") + " audio").trim();
            String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);
            String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=3&q="
                    + encoded + "&key=" + effectiveApiKey;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode items = root.path("items");
                if (items.isArray()) {
                    for (JsonNode it : items) {
                        String vid = it.path("id").path("videoId").asText();
                        String vTitle = it.path("snippet").path("title").asText();
                        if (!vid.isBlank() && isTrackTitleMatch(trackTitle, vTitle, artistName, albumTitle)) {
                            log.info("Found single track video ID [{}] (\"{}\") for track [{}] via YouTube Data API search", vid, vTitle, trackTitle);
                            return vid;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Single-track search failed for [{} - {}]: {}", artistName, trackTitle, ex.getMessage());
        }
        return null;
    }

    public int scoreCandidatePlaylist(String pid, String pTitle, String channelTitle, String artistName, String albumTitle) {
        int score = 0;
        String normAlbum = normalizeTitle(albumTitle);
        String normArtist = normalizeTitle(artistName);
        String normChannel = normalizeTitle(channelTitle);
        String normPTitle = normalizeTitle(pTitle);

        // 1. YouTube Music official auto-generated album playlists always begin with OLAK5uy_
        if (pid != null && pid.startsWith("OLAK5uy_")) {
            score += 100;
        }

        // 2. Channel preference: Strongly prioritize official artist channel, Topic channel, and verified variants
        if (!normArtist.isBlank()) {
            if (normChannel.equals(normArtist)) {
                // Exact channel name match (e.g. Channel "American Football" for Artist "American Football")
                score += 70;
            } else if (normChannel.equals(normArtist + " topic") || (normChannel.endsWith("topic") && normChannel.contains(normArtist))) {
                // Official Topic channel (e.g. "American Football - Topic")
                score += 60;
            } else if (normChannel.equals(normArtist + " official")
                    || normChannel.equals(normArtist + " music")
                    || normChannel.equals(normArtist + " vevo")) {
                // Official artist channel variants (e.g. "Radiohead Official", "Daft Punk VEVO")
                score += 55;
            } else if (normChannel.contains(normArtist)) {
                // Channel title includes artist name
                score += 35;
            }
        } else if (normChannel.endsWith("topic")) {
            score += 20;
        }

        // 3. Playlist title similarity
        if (!normAlbum.isBlank()) {
            if (normPTitle.contains(normAlbum)) {
                score += 40;
            } else if (normAlbum.contains(normPTitle)) {
                score += 20;
            }
        }

        // 4. Boost for containing "album" or "full album"
        if (normPTitle.contains("full album") || normPTitle.contains("album")) {
            score += 10;
        }

        // 5. Heavy penalty for covers, tributes, karaoke when original album is not a cover/tribute
        boolean albumIsCoverOrTribute = isCoverOrTributeText(albumTitle);
        boolean playlistIsCoverOrTribute = isCoverOrTributeText(pTitle);
        if (!albumIsCoverOrTribute && playlistIsCoverOrTribute) {
            score -= 120;
        }

        // 6. Penalty for "various artists" or compilation playlists when artist is a specific band
        if (!normArtist.contains("various artists") && (normPTitle.contains("various artists") || normChannel.contains("various artists"))) {
            score -= 40;
        }

        return score;
    }

    private boolean isCoverOrTributeText(String text) {
        if (text == null || text.isBlank()) return false;
        String s = text.toLowerCase();
        // Ignore non-tribute uses of cover like "album cover", "cover art", "under cover", "cover photo"
        s = s.replaceAll("(?i)\\b(album\\s*cover|cover\\s*art|under\\s*cover|cover\\s*photo)\\b", " ");
        return s.matches(".*\\b(covers?|tribute|tributes|karaoke)\\b.*");
    }

    private boolean isForeignArtistVideo(String videoTitle, String artistName, String trackTitle, String albumTitle) {
        if (videoTitle == null || artistName == null || trackTitle == null) return false;
        String normArtist = normalizeTitle(artistName);
        if (normArtist.isBlank() || normArtist.equals("various artists") || normArtist.equals("soundtrack")) {
            return false;
        }

        int dashIdx = videoTitle.indexOf('-');
        if (dashIdx <= 0) return false;

        String prefix = normalizeTitle(videoTitle.substring(0, dashIdx));
        String suffix = normalizeTitle(videoTitle.substring(dashIdx + 1));
        String normTrack = normalizeTitle(trackTitle);
        String normAlbum = normalizeTitle(albumTitle);

        // If track title is on the right side of the dash (e.g. "Artist - Track")
        if (suffix.contains(normTrack)) {
            // If prefix is just track numbers or vinyl sides (e.g. "01", "1", "A1", "Track 1")
            if (prefix.matches("^(track\\s*)?\\d+[a-z]?$") || prefix.matches("^[a-d]\\d?$")) {
                return false;
            }
            // If prefix contains the artist name or album title
            if (!normArtist.isBlank() && prefix.contains(normArtist)) {
                return false;
            }
            if (!normAlbum.isBlank() && prefix.contains(normAlbum)) {
                return false;
            }
            // If full video title mentions artist, check if it's explicitly labeled as a cover
            String normFullVideo = normalizeTitle(videoTitle);
            if (!normArtist.isBlank() && normFullVideo.contains(normArtist)) {
                return isCoverOrTributeText(videoTitle);
            }
            // Prefix is an entirely different artist name (e.g. "Iron & Wine - Never Meant")
            return true;
        }

        return false;
    }

    private String normalizeTitle(String title) {
        if (title == null) return "";
        String norm = Normalizer.normalize(title, Normalizer.Form.NFD);
        norm = norm.replaceAll("\\p{M}", "");
        norm = norm.replaceAll("[‐‑–—]", "-");
        norm = norm.replaceAll("['’`]", "");
        norm = norm.replaceAll("[^a-zA-Z0-9\\s]", " ");
        return norm.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private String cleanVideoTitle(String videoTitle, String artistName) {
        if (videoTitle == null) return "";
        String s = videoTitle;
        if (artistName != null && !artistName.isBlank()) {
            s = s.replaceAll("(?i)" + Pattern.quote(artistName), " ");
        }
        s = s.replaceAll("(?i)\\b(official\\s*(music\\s*)?video|official\\s*audio|visualizer|lyrics?|full\\s*album|remaster(ed)?|album\\s*version|hq|hd|4k|deluxe|audio)\\b", " ");
        s = s.replaceAll("(?i)\\b(live\\s+(at|in)\\s+[^)]+)\\b", " ");
        return normalizeTitle(s);
    }

    public boolean isTrackTitleMatch(String trackTitle, String videoTitle, String artistName) {
        return isTrackTitleMatch(trackTitle, videoTitle, artistName, null);
    }

    public boolean isTrackTitleMatch(String trackTitle, String videoTitle, String artistName, String albumTitle) {
        if (trackTitle == null || videoTitle == null) return false;

        // Reject covers, tributes, karaoke if album/track is not itself a cover or tribute
        boolean albumOrTrackIsCover = isCoverOrTributeText(albumTitle) || isCoverOrTributeText(trackTitle);
        if (!albumOrTrackIsCover && isCoverOrTributeText(videoTitle)) {
            return false;
        }

        // Reject videos where another artist is performing this track (e.g. "Iron & Wine - Never Meant")
        if (isForeignArtistVideo(videoTitle, artistName, trackTitle, albumTitle)) {
            return false;
        }

        String normTrack = normalizeTitle(trackTitle);
        String cleanedVideo = cleanVideoTitle(videoTitle, artistName);
        String normVideo = normalizeTitle(videoTitle);

        if (normTrack.isBlank() || normVideo.isBlank()) return false;

        // Direct containment (only check cleanedVideo if it has non-blank substantive content)
        if (!cleanedVideo.isBlank()) {
            if (cleanedVideo.contains(normTrack)) return true;
            if (cleanedVideo.length() >= 3 && normTrack.contains(cleanedVideo)) return true;
        }
        if (!normTrack.isBlank() && normVideo.contains(normTrack)) return true;

        // Non-trivial word overlap
        String[] words = normTrack.split(" ");
        List<String> significantWords = Arrays.stream(words)
                .filter(w -> w.length() > 2)
                .toList();

        if (!significantWords.isEmpty()) {
            long matchCount = significantWords.stream()
                    .filter(w -> cleanedVideo.contains(w) || normVideo.contains(w))
                    .count();
            if (matchCount == significantWords.size()) {
                return true;
            }
        }

        return false;
    }

    public int calculatePlaylistMatchScore(List<Track> albumTracks, List<PlaylistItem> playlistItems, String artistName) {
        return calculatePlaylistMatchScore(albumTracks, playlistItems, artistName, null);
    }

    public int calculatePlaylistMatchScore(List<Track> albumTracks, List<PlaylistItem> playlistItems, String artistName, String albumTitle) {
        int matches = 0;
        for (int i = 0; i < albumTracks.size(); i++) {
            Track t = albumTracks.get(i);
            // Check if track at index i matches playlist item at index i
            if (i < playlistItems.size() && isTrackTitleMatch(t.getTitle(), playlistItems.get(i).title(), artistName, albumTitle)) {
                matches++;
            } else {
                // Check if matches anywhere in playlist
                boolean found = playlistItems.stream()
                        .anyMatch(item -> isTrackTitleMatch(t.getTitle(), item.title(), artistName, albumTitle));
                if (found) {
                    matches++;
                }
            }
        }
        return matches;
    }
}


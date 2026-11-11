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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.aficio.catalog.CatalogNormalizationUtils;
import com.aficio.catalog.CatalogService;
import com.aficio.security.VerifiedIdentity;

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

    @Value("${aficio.owner-emails:}")
    private String configuredOwnerEmails = "";

    public void setConfiguredOwnerEmails(String emails) {
        this.configuredOwnerEmails = emails != null ? emails : "";
    }

    // Distinct album IDs synced via YouTube API per client identifier (in-memory fallback)
    private final ConcurrentHashMap<String, Set<Long>> clientSyncedAlbums = new ConcurrentHashMap<>();

    // Single-flight deduplication to prevent parallel request stampedes on YouTube API for the same album
    private final ConcurrentHashMap<Long, CompletableFuture<Boolean>> inFlightAlbumSyncs = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aficio.artist.ArtistRepository artistRepository;

    public void setArtistRepository(com.aficio.artist.ArtistRepository artistRepository) {
        this.artistRepository = artistRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SystemQuotaAuditTracker systemQuotaAuditTracker;

    public void setSystemQuotaAuditTracker(SystemQuotaAuditTracker tracker) {
        this.systemQuotaAuditTracker = tracker;
    }

    public SystemQuotaAuditTracker getSystemQuotaAuditTracker() {
        if (this.systemQuotaAuditTracker == null) {
            this.systemQuotaAuditTracker = new SystemQuotaAuditTracker(null, objectMapper);
        }
        return this.systemQuotaAuditTracker;
    }

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

    public String extractEmail(String clientId) {
        if (clientId == null || !clientId.startsWith("user:")) return null;
        String raw = clientId.substring(5);
        int colon = raw.indexOf(':');
        return colon > 0 ? raw.substring(colon + 1) : null;
    }

    public VerifiedIdentity parseIdentityFromClientId(String clientId) {
        boolean isOwner = isOwnerAccount(clientId);
        if (clientId != null && clientId.startsWith("user:")) {
            return VerifiedIdentity.authenticated(extractUsername(clientId), extractEmail(clientId), isOwner);
        }
        String ip = (clientId != null && clientId.startsWith("ip:")) ? clientId.substring(3) : clientId;
        return VerifiedIdentity.anonymous(ip);
    }

    public String resolveUserCloudApiKey(String clientId) {
        return null;
    }

    public boolean isOwnerAccount(String clientId) {
        if (clientId == null || configuredOwnerEmails == null || configuredOwnerEmails.isBlank()) return false;
        String email = extractEmail(clientId);
        String username = extractUsername(clientId);
        String[] parts = configuredOwnerEmails.split(",");
        for (String part : parts) {
            String owner = part.trim().toLowerCase();
            if (owner.isEmpty()) continue;
            if (email != null && email.trim().equalsIgnoreCase(owner)) return true;
            if (username != null && username.trim().equalsIgnoreCase(owner)) return true;
            if (clientId.toLowerCase().contains(owner)) return true;
        }
        return false;
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
        return 0;
    }

    public boolean canSyncAlbumViaYouTube(String clientId, Long albumId) {
        return false;
    }

    public boolean canSyncAlbumViaYouTube(String clientId, Long albumId, boolean hasCustomKey) {
        return false;
    }

    public boolean canSyncAlbumViaYouTube(String clientId, Long albumId, boolean hasCustomKey, boolean isDev) {
        return false;
    }

    public void recordAlbumSynced(String clientId, Long albumId) {
        // No-op: external syncs disabled
    }

    public UserQuotaResponse getUserQuota(VerifiedIdentity identity, String customApiKey) {
        String clientId = identity != null ? identity.toClientId(null) : "anonymous";
        return new UserQuotaResponse(
                clientId,
                false,
                false,
                0,
                0,
                0,
                false,
                false
        );
    }

    public UserQuotaResponse getUserQuota(String clientId, String customApiKey) {
        return getUserQuota(parseIdentityFromClientId(clientId), customApiKey);
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
        return getPlayback(trackId, parseIdentityFromClientId(clientId), customApiKey);
    }

    @Transactional
    public TrackPlaybackResponse getPlayback(Long trackId, VerifiedIdentity identity, String customApiKey) {
        getSystemQuotaAuditTracker().startRequestTracking();
        try {
            return doGetPlayback(trackId, identity, customApiKey);
        } finally {
            getSystemQuotaAuditTracker().finishRequestTracking();
        }
    }

    private TrackPlaybackResponse doGetPlayback(Long trackId, VerifiedIdentity identity, String customApiKey) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found with id: " + trackId));

        Album album = track.getAlbum();
        String artistName = (album != null && album.getArtist() != null) ? album.getArtist().getName() : "Unknown Artist";
        String albumTitle = (album != null) ? album.getTitle() : "Unknown Album";
        Long albumId = (album != null) ? album.getId() : null;
        String coverArtUrl = (album != null) ? album.getCoverArtUrl() : null;

        String clientId = identity != null ? identity.toClientId(null) : "anonymous";
        boolean isDevAccount = identity != null && identity.isDevAccount();
        boolean hasCustomKey = customApiKey != null && !customApiKey.isBlank();

        // 1. Instant Cache: Check if already stored in database (0 external calls)
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
                    0,
                    0,
                    0,
                    0,
                    null,
                    null,
                    null
            );
        }

        String youtubeVideoId = null;
        String source = null;

        // 2. MusicBrainz Recording Relations Check (Optional)
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

        // 3. Local Catalog Lookup (0 external calls - PostgreSQL youtube_catalog)
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

        // 4. If not found in local catalog or cache, track is unavailable (0 external API calls)
        if (youtubeVideoId == null) {
            source = "unavailable";
        } else {
            recordUserAlbumHistory(clientId, album, false);
        }

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
                source,
                false,
                0,
                0,
                0,
                0,
                null,
                null,
                null
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

                // 1. Purge from youtube_catalog so 12M cache doesn't re-serve the restricted ID
                if (jdbcTemplate != null) {
                    try {
                        int purged = jdbcTemplate.update("DELETE FROM youtube_catalog WHERE song_id = ?", badId);
                        if (purged > 0) {
                            log.info("Purged restricted video ID [{}] from youtube_catalog ({} rows deleted)", badId, purged);
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to purge restricted ID [{}] from youtube_catalog: {}", badId, ex.getMessage());
                    }
                }

                // 2. Blacklist in Redis to prevent playlist auto-discovery from re-infecting tracks
                if (stringRedisTemplate != null) {
                    try {
                        stringRedisTemplate.opsForSet().add("blacklist:youtube_restricted", badId);
                        log.info("Blacklisted restricted video ID [{}] in Redis", badId);
                    } catch (Exception ex) {
                        log.warn("Failed to add restricted ID [{}] to Redis blacklist: {}", badId, ex.getMessage());
                    }
                }
            }
        });
    }

    public boolean isRestrictedVideoId(String videoId) {
        if (videoId == null || videoId.isBlank()) return false;
        if (stringRedisTemplate != null) {
            try {
                Boolean member = stringRedisTemplate.opsForSet().isMember("blacklist:youtube_restricted", videoId);
                return Boolean.TRUE.equals(member);
            } catch (Exception ex) {
                log.debug("Redis blacklist check failed for [{}]: {}", videoId, ex.getMessage());
            }
        }
        return false;
    }

    public boolean isRestrictedPlaylistId(String playlistId) {
        if (playlistId == null || playlistId.isBlank()) return false;
        if (stringRedisTemplate != null) {
            try {
                Boolean member = stringRedisTemplate.opsForSet().isMember("blacklist:playlist_restricted", playlistId);
                return Boolean.TRUE.equals(member);
            } catch (Exception ex) {
                log.debug("Redis blacklist check failed for playlist [{}]: {}", playlistId, ex.getMessage());
            }
        }
        return false;
    }

    @Transactional
    public TrackPlaybackResponse handleRestrictedTrackAndResolveUnofficial(Long trackId, String clientId, String customApiKey) {
        return handleRestrictedTrackAndResolveUnofficial(trackId, parseIdentityFromClientId(clientId), customApiKey);
    }

    @Transactional
    public TrackPlaybackResponse handleRestrictedTrackAndResolveUnofficial(Long trackId, VerifiedIdentity identity, String customApiKey) {
        if (trackId == null) return null;
        getSystemQuotaAuditTracker().startRequestTracking();
        Track track = trackRepository.findById(trackId).orElse(null);
        if (track == null) {
            getSystemQuotaAuditTracker().finishRequestTracking();
            return null;
        }

        Album album = track.getAlbum();
        String badVideoId = track.getYoutubeVideoId();

        // 1. Invalidate and blacklist this restricted video ID
        invalidateRestrictedYoutubeVideoId(trackId);

        // 2. Blacklist stored playlist if it is official or linked to this restricted track
        if (album != null) {
            String storedPlaylistId = album.getYoutubePlaylistId();
            if (storedPlaylistId != null && !storedPlaylistId.isBlank()) {
                log.info("Blacklisting stored playlist [{}] for album [{}] due to restricted track [{}]",
                        storedPlaylistId, album.getTitle(), track.getTitle());
                if (stringRedisTemplate != null) {
                    try {
                        stringRedisTemplate.opsForSet().add("blacklist:playlist_restricted", storedPlaylistId);
                    } catch (Exception ex) {
                        log.warn("Failed to add playlist [{}] to Redis blacklist: {}", storedPlaylistId, ex.getMessage());
                    }
                }
                album.setYoutubePlaylistId(null);
                albumRepository.save(album);
            }

            // Clear any other tracks on this album that had the restricted video ID
            List<Track> allAlbumTracks = trackRepository.findByAlbumIdOrderByPosition(album.getId());
            boolean clearedAny = false;
            for (Track t : allAlbumTracks) {
                if (badVideoId != null && badVideoId.equals(t.getYoutubeVideoId())) {
                    t.setYoutubeVideoId(null);
                    clearedAny = true;
                }
            }
            if (clearedAny) {
                trackRepository.saveAll(allAlbumTracks);
            }

        }

        // 4. Return new playback response for this track
        return getPlayback(trackId, identity, customApiKey);
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

    public List<Map<String, Object>> findAlbumCatalogRows(String artistName, Set<String> artistVariants, String albumTitle) {
        if (jdbcTemplate == null || albumTitle == null || albumTitle.isBlank()) return Collections.emptyList();
        String cleanAlbum = CatalogService.cleanAlbumTitleStatic(albumTitle);
        boolean isLive = CatalogNormalizationUtils.isLiveOrAcousticTitle(albumTitle);

        Set<String> allVariants = new LinkedHashSet<>();
        if (artistName != null && !artistName.isBlank()) allVariants.add(artistName.trim());
        if (artistVariants != null) allVariants.addAll(artistVariants);

        // 1. Direct equality check
        String exactSql = "SELECT song_id, title, duration FROM youtube_catalog WHERE LOWER(artist) = LOWER(?) AND (LOWER(album) = LOWER(?) OR LOWER(album) = LOWER(?)) ORDER BY title";
        for (String variant : allVariants) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(exactSql, variant, albumTitle, cleanAlbum);
                if (!rows.isEmpty()) {
                    return rows;
                }
            } catch (Exception ignored) {}
        }

        // 2. Direct prefix/edition match
        String sql = """
            SELECT song_id, title, duration, album FROM youtube_catalog
            WHERE LOWER(artist) = LOWER(?)
              AND (
                LOWER(album) = LOWER(?)
                OR LOWER(album) = LOWER(?)
                OR LOWER(album) LIKE LOWER(?) || ' (%'
                OR LOWER(album) LIKE LOWER(?) || ' - %'
                OR LOWER(album) LIKE LOWER(?) || ':%'
                OR LOWER(album) LIKE LOWER(?) || ' [%'
                OR LOWER(album) LIKE LOWER(?) || ' (%'
                OR LOWER(album) LIKE LOWER(?) || ' - %'
              )
            ORDER BY (CASE WHEN LOWER(album) = LOWER(?) THEN 0 ELSE 1 END), duration DESC
        """;

        for (String variant : allVariants) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        sql, variant, albumTitle, cleanAlbum, albumTitle, albumTitle, albumTitle, albumTitle, cleanAlbum, cleanAlbum, albumTitle
                );
                if (!rows.isEmpty()) {
                    return rows;
                }
            } catch (Exception ignored) {}
        }

        // 3. Scan distinct candidate albums by artist in catalog and check isAlbumMatch
        for (String variant : allVariants) {
            try {
                List<String> candAlbums = jdbcTemplate.query(
                        "SELECT DISTINCT album FROM youtube_catalog WHERE LOWER(artist) = LOWER(?)",
                        (rs, rowNum) -> rs.getString("album"),
                        variant
                );
                List<String> matchedCandAlbums = new ArrayList<>();
                for (String cand : candAlbums) {
                    if (cand == null || cand.isBlank()) continue;
                    if (isLive && !CatalogNormalizationUtils.isLiveOrAcousticTitle(cand)) continue;
                    if (!isLive && CatalogNormalizationUtils.isLiveOrAcousticTitle(cand)) continue;
                    if (CatalogService.isAlbumMatch(albumTitle, cand) || CatalogService.isAlbumMatch(cleanAlbum, cand)) {
                        matchedCandAlbums.add(cand);
                    }
                }
                if (!matchedCandAlbums.isEmpty()) {
                    StringBuilder inQuery = new StringBuilder("SELECT song_id, title, duration, album FROM youtube_catalog WHERE LOWER(artist) = LOWER(?) AND album IN (");
                    for (int i = 0; i < matchedCandAlbums.size(); i++) {
                        if (i > 0) inQuery.append(",");
                        inQuery.append("'").append(matchedCandAlbums.get(i).replace("'", "''")).append("'");
                    }
                    inQuery.append(") ORDER BY duration DESC");
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(inQuery.toString(), variant);
                    if (!rows.isEmpty()) {
                        return rows;
                    }
                }
            } catch (Exception ignored) {}
        }

        return Collections.emptyList();
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

            // 1. Gather artist variants (MBID aliases, transliteration & vowel variants, and catalog candidates)
            Set<String> artistVariants = new LinkedHashSet<>();
            artistVariants.addAll(CatalogNormalizationUtils.generateArtistVariants(artistName));
            if (album != null && album.getArtist() != null && album.getArtist().getMusicBrainzId() != null && artistRepository != null) {
                try {
                    List<com.aficio.artist.Artist> mbidArtists = artistRepository.findAllByMusicBrainzId(album.getArtist().getMusicBrainzId());
                    for (com.aficio.artist.Artist a : mbidArtists) {
                        if (a.getName() != null) {
                            artistVariants.addAll(CatalogNormalizationUtils.generateArtistVariants(a.getName()));
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 2. High-precision Album Level Resolution (0 quota units)
            // If album title is present, fetch the album's catalog tracklist and match via duration + phonetic title
            if (!albumTitle.isBlank()) {
                List<Map<String, Object>> albumCatalogRows = findAlbumCatalogRows(artistName, artistVariants, albumTitle);

                if (!albumCatalogRows.isEmpty()) {
                    for (Map<String, Object> crow : albumCatalogRows) {
                        String rawTitle = (String) crow.get("title");
                        Integer durSec = crow.get("duration") != null ? ((Number) crow.get("duration")).intValue() : null;
                        if (CatalogNormalizationUtils.isTrackFuzzyMatch(title, track.getLengthMilliseconds(), rawTitle, durSec)) {
                            String videoId = (String) crow.get("song_id");
                            if (videoId != null && !videoId.isBlank()) {
                                videoId = videoId.trim();
                                track.setYoutubeVideoId(videoId);
                                trackRepository.save(track);
                                log.info("Found YouTube video ID [{}] for track [{}] by [{}] via album catalog matching (0 quota units)",
                                        videoId, track.getTitle(), artistName);

                                if (album != null && album.getId() != null) {
                                    populateSiblingTracksFromCatalog(album, artistName, albumCatalogRows);
                                }
                                return videoId;
                            }
                        }
                    }
                }
            }

            // 3. Fallback: Search youtube_catalog across artist variants by title
            // STRICT GUARDRAIL: If this is a Live / Unplugged / Acoustic album, NEVER cross-match with studio albums!
            boolean isLiveAlbum = CatalogNormalizationUtils.isLiveOrAcousticTitle(albumTitle);
            if (isLiveAlbum) {
                log.info("Skipping cross-album studio fallback for live/unplugged album [{}]", albumTitle);
                return null;
            }

            String sql = """
                SELECT song_id FROM youtube_catalog
                WHERE LOWER(artist) = LOWER(?)
                  AND LOWER(title) = LOWER(?)
                ORDER BY (CASE WHEN ? != '' AND LOWER(album) = LOWER(?) THEN 0 ELSE 1 END), duration DESC
                LIMIT 1
            """;

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

            String normalized = normalizeTitleForCatalog(title);
            String cleanBase = normalizeTitleForCatalog(title);

            for (String variant : artistVariants) {
                List<String> results = jdbcTemplate.query(
                        sql,
                        (rs, rowNum) -> rs.getString("song_id"),
                        variant, title, albumTitle, albumTitle
                );

                if (results.isEmpty() && !normalized.equalsIgnoreCase(title) && !normalized.isBlank()) {
                    results = jdbcTemplate.query(
                            sql,
                            (rs, rowNum) -> rs.getString("song_id"),
                            variant, normalized, albumTitle, albumTitle
                    );
                }

                if (results.isEmpty() && !cleanBase.isBlank()) {
                    results = jdbcTemplate.query(
                            patternSql,
                            (rs, rowNum) -> rs.getString("song_id"),
                            variant, cleanBase, cleanBase, cleanBase, cleanBase, albumTitle, albumTitle
                    );
                }

                if (!results.isEmpty() && results.get(0) != null && !results.get(0).isBlank()) {
                    String videoId = results.get(0).trim();
                    track.setYoutubeVideoId(videoId);
                    trackRepository.save(track);
                    log.info("Found YouTube video ID [{}] for track [{}] by [{}] via LAION-DISCO-12M catalog (0 quota units)",
                            videoId, track.getTitle(), variant);

                    if (album != null && album.getId() != null) {
                        populateSiblingTracksFromCatalog(album, variant, Collections.emptyList());
                    }
                    return videoId;
                }
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
        populateSiblingTracksFromCatalog(album, artistName, Collections.emptyList());
    }

    public void populateSiblingTracksFromCatalog(Album album, String artistName, List<Map<String, Object>> preloadedRows) {
        if (jdbcTemplate == null || album == null || album.getId() == null) return;
        try {
            List<Track> albumTracks = trackRepository.findByAlbumIdOrderByPosition(album.getId());
            boolean isLiveAlbum = CatalogNormalizationUtils.isLiveOrAcousticTitle(album.getTitle());

            // For live albums, also inspect tracks whose existing IDs might be bad studio matches
            List<Track> missingTracks = albumTracks.stream()
                    .filter(t -> t.getYoutubeVideoId() == null || t.getYoutubeVideoId().isBlank() || isLiveAlbum)
                    .toList();

            if (missingTracks.isEmpty()) return;

            String albumTitle = album.getTitle() != null ? album.getTitle().trim() : "";

            Set<String> artistVariants = new LinkedHashSet<>();
            artistVariants.addAll(CatalogNormalizationUtils.generateArtistVariants(artistName));
            if (album.getArtist() != null && album.getArtist().getMusicBrainzId() != null && artistRepository != null) {
                try {
                    List<com.aficio.artist.Artist> mbidArtists = artistRepository.findAllByMusicBrainzId(album.getArtist().getMusicBrainzId());
                    for (com.aficio.artist.Artist a : mbidArtists) {
                        if (a.getName() != null) {
                            artistVariants.addAll(CatalogNormalizationUtils.generateArtistVariants(a.getName()));
                        }
                    }
                } catch (Exception ignored) {}
            }

            List<Map<String, Object>> albumCatalogRows = (preloadedRows != null && !preloadedRows.isEmpty())
                    ? preloadedRows
                    : findAlbumCatalogRows(artistName, artistVariants, albumTitle);

            int populatedCount = 0;
            List<Track> toSave = new ArrayList<>();
            Set<String> usedSongIds = new HashSet<>();

            for (Track t : missingTracks) {
                String title = t.getTitle() != null ? t.getTitle().trim() : "";
                if (title.isBlank()) continue;

                // 1. Match from album catalog rows with duration + fuzzy phonetic
                String matchedVid = null;
                for (Map<String, Object> crow : albumCatalogRows) {
                    String songId = (String) crow.get("song_id");
                    if (songId == null || usedSongIds.contains(songId)) continue;
                    String rawTitle = (String) crow.get("title");
                    Integer durSec = crow.get("duration") != null ? ((Number) crow.get("duration")).intValue() : null;
                    if (CatalogNormalizationUtils.isTrackFuzzyMatch(title, t.getLengthMilliseconds(), rawTitle, durSec)) {
                        matchedVid = songId;
                        break;
                    }
                }

                // 2. If not matched in album catalog rows, try fallback queries across catalog ONLY if not a live/acoustic album
                if (matchedVid == null && !isLiveAlbum) {
                    String sql = """
                        SELECT song_id, duration, album, title FROM youtube_catalog
                        WHERE LOWER(artist) = LOWER(?)
                          AND LOWER(title) = LOWER(?)
                        ORDER BY (CASE WHEN ? != '' AND LOWER(album) = LOWER(?) THEN 0 ELSE 1 END), duration DESC
                        LIMIT 5
                    """;
                    for (String variant : artistVariants) {
                        List<Map<String, Object>> candidateRows = jdbcTemplate.queryForList(sql, variant, title, albumTitle, albumTitle);
                        if (candidateRows.isEmpty()) {
                            String normalized = normalizeTitleForCatalog(title);
                            if (!normalized.equalsIgnoreCase(title) && !normalized.isBlank()) {
                                candidateRows = jdbcTemplate.queryForList(sql, variant, normalized, albumTitle, albumTitle);
                            }
                        }
                        for (Map<String, Object> crow : candidateRows) {
                            Integer durSec = crow.get("duration") != null ? ((Number) crow.get("duration")).intValue() : null;
                            if (t.getLengthMilliseconds() != null && t.getLengthMilliseconds() > 0) {
                                if (!CatalogNormalizationUtils.isDurationMatch(t.getLengthMilliseconds(), durSec)) {
                                    continue;
                                }
                            }
                            String songId = (String) crow.get("song_id");
                            if (songId != null && !usedSongIds.contains(songId)) {
                                matchedVid = songId.trim();
                                break;
                            }
                        }
                        if (matchedVid != null) break;
                    }
                }

                if (matchedVid != null && !matchedVid.isBlank()) {
                    if (!matchedVid.equals(t.getYoutubeVideoId())) {
                        t.setYoutubeVideoId(matchedVid.trim());
                        toSave.add(t);
                        populatedCount++;
                    }
                    usedSongIds.add(matchedVid.trim());
                }
            }

            if (!toSave.isEmpty()) {
                trackRepository.saveAll(toSave);
                log.info("Batch populated/updated {}/{} sibling tracks for album [{}] from youtube_catalog (0 units)",
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
        return false;
    }

    private boolean populateAlbumPlaylistTracks(Album album, String clientId, String effectiveApiKey, boolean isCustomKey, boolean preferUnofficial) {
        return false;
    }

    private boolean doPopulateAlbumPlaylistTracks(Album album, String clientId, String effectiveApiKey, boolean isCustomKey, boolean preferUnofficial) {
        return false;
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
     * Retrieves candidate playlists and scores them to prioritize official or unofficial releases based on preference.
     */
    private List<CandidatePlaylist> searchYouTubeForCandidatePlaylists(String artistName, String albumTitle, String clientId, Long albumId, String effectiveApiKey, boolean isCustomKey) {
        return Collections.emptyList();
    }

    private List<CandidatePlaylist> searchYouTubeForCandidatePlaylists(String artistName, String albumTitle, String clientId, Long albumId, String effectiveApiKey, boolean isCustomKey, boolean preferUnofficial) {
        return Collections.emptyList();
    }

    private List<PlaylistItem> fetchPlaylistItems(String playlistId, String effectiveApiKey) {
        return Collections.emptyList();
    }

    private String searchYouTubeForSingleTrack(String artistName, String trackTitle, String albumTitle, String effectiveApiKey) {
        return null;
    }

    private String searchYouTubeForSingleTrack(String artistName, String trackTitle, String albumTitle, String effectiveApiKey, boolean preferUnofficial) {
        return null;
    }

    public int scoreCandidatePlaylist(String pid, String pTitle, String channelTitle, String artistName, String albumTitle) {
        return scoreCandidatePlaylist(pid, pTitle, channelTitle, artistName, albumTitle, false);
    }

    public int scoreCandidatePlaylist(String pid, String pTitle, String channelTitle, String artistName, String albumTitle, boolean preferUnofficial) {
        if (isRestrictedPlaylistId(pid)) {
            return -999;
        }

        int score = 0;
        String normAlbum = normalizeTitle(albumTitle);
        String normArtist = normalizeTitle(artistName);
        String normChannel = normalizeTitle(channelTitle);
        String normPTitle = normalizeTitle(pTitle);

        if (preferUnofficial) {
            // Unofficial mode: penalize official YouTube Music / Topic / VEVO playlists that block embedding
            if (pid != null && pid.startsWith("OLAK5uy_")) {
                score -= 300;
            }
            if (normChannel.contains("topic") || normChannel.contains("vevo") || normChannel.equals(normArtist + " official")) {
                score -= 200;
            }
            if (pid != null && pid.startsWith("PL")) {
                score += 80; // Standard user-created playlists begin with "PL"
            }
            if (normPTitle.contains("full album") || normPTitle.contains("album") || normPTitle.contains("complete")) {
                score += 30;
            }
            if (!normAlbum.isBlank() && normPTitle.contains(normAlbum)) {
                score += 50;
            }
            if (!normArtist.isBlank() && (normPTitle.contains(normArtist) || normChannel.contains(normArtist))) {
                score += 30;
            }
        } else {
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
            if (significantWords.size() >= 3 && (double) matchCount / significantWords.size() >= 0.75) {
                return true;
            }
        }

        // Transliteration & phonetic match for regional / romanized Indic titles
        if (CatalogNormalizationUtils.isTrackFuzzyMatch(trackTitle, null, cleanedVideo, null)
                || CatalogNormalizationUtils.isTrackFuzzyMatch(trackTitle, null, videoTitle, null)) {
            return true;
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


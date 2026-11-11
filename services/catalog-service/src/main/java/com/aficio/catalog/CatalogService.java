package com.aficio.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aficio.album.Album;
import com.aficio.album.AlbumRepository;
import com.aficio.artist.Artist;
import com.aficio.artist.ArtistRepository;
import com.aficio.coverart.CoverArtClient;
import com.aficio.feed.FeedReleaseResponse;
import com.aficio.history.UserAlbumHistory;
import com.aficio.history.UserAlbumHistoryRepository;
import com.aficio.musicbrainz.MusicBrainzArtistCredit;
import com.aficio.musicbrainz.MusicBrainzClient;
import com.aficio.musicbrainz.MusicBrainzMedia;
import com.aficio.musicbrainz.MusicBrainzRecording;
import com.aficio.musicbrainz.MusicBrainzRelease;
import com.aficio.musicbrainz.MusicBrainzReleaseDetails;
import com.aficio.musicbrainz.MusicBrainzReleaseSearchResponse;
import com.aficio.musicbrainz.MusicBrainzSearchResponse;
import com.aficio.musicbrainz.MusicBrainzTrack;
import com.aficio.track.Track;
import com.aficio.track.TrackRepository;
import com.aficio.musicbrainz.MusicBrainzArtist;
import com.aficio.musicbrainz.MusicBrainzArtistReference;
import com.aficio.musicbrainz.MusicBrainzReleaseGroup;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final TrackRepository trackRepository;
    private final MusicBrainzClient musicBrainzClient;
    private final CoverArtClient coverArtClient;
    private final UserAlbumHistoryRepository userAlbumHistoryRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CatalogService(
            AlbumRepository albumRepository,
            ArtistRepository artistRepository,
            TrackRepository trackRepository,
            MusicBrainzClient musicBrainzClient,
            CoverArtClient coverArtClient,
            UserAlbumHistoryRepository userAlbumHistoryRepository,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider
    ) {
        this.albumRepository = albumRepository;
        this.artistRepository = artistRepository;
        this.trackRepository = trackRepository;
        this.musicBrainzClient = musicBrainzClient;
        this.coverArtClient = coverArtClient;
        this.userAlbumHistoryRepository = userAlbumHistoryRepository;
        this.jdbcTemplate = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
    }

    public CatalogService(
            AlbumRepository albumRepository,
            ArtistRepository artistRepository,
            TrackRepository trackRepository,
            MusicBrainzClient musicBrainzClient,
            CoverArtClient coverArtClient
    ) {
        this(albumRepository, artistRepository, trackRepository, musicBrainzClient, coverArtClient, null, null);
    }

    public CatalogService(
            AlbumRepository albumRepository,
            ArtistRepository artistRepository,
            TrackRepository trackRepository,
            MusicBrainzClient musicBrainzClient,
            CoverArtClient coverArtClient,
            UserAlbumHistoryRepository userAlbumHistoryRepository
    ) {
        this(albumRepository, artistRepository, trackRepository, musicBrainzClient, coverArtClient, userAlbumHistoryRepository, null);
    }

    @Transactional
    @CacheEvict(value = {"musicbrainzArtists", "musicbrainzReleases", "musicbrainzReleaseDetails", "musicbrainzArtistReleases", "musicbrainzArtistDetails"}, allEntries = true)
    public Album syncAlbum(String musicBrainzId) {
        if (musicBrainzId != null && musicBrainzId.startsWith("yt-")) {
            return syncAlbumFromYoutubeCatalog(musicBrainzId);
        }

        // 1. Short-circuit: If album already exists locally with tracks, return it directly without network call
        Album existingAlbum = albumRepository.findFirstByMusicBrainzIdOrderByIdAsc(musicBrainzId).orElse(null);
        if (existingAlbum != null && trackRepository.countByAlbumId(existingAlbum.getId()) > 0) {
            reconcileTracksWithDataset(existingAlbum);
            return existingAlbum;
        }

        MusicBrainzReleaseDetails details = musicBrainzClient.getReleaseDetails(musicBrainzId);
        if (details == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found on MusicBrainz for id: " + musicBrainzId);
        }

        String canonicalReleaseId = details.id() != null ? details.id() : musicBrainzId;

        // 2. Check if album already exists under the canonical release ID
        if (existingAlbum == null && !canonicalReleaseId.equalsIgnoreCase(musicBrainzId)) {
            existingAlbum = albumRepository.findFirstByMusicBrainzIdOrderByIdAsc(canonicalReleaseId).orElse(null);
            if (existingAlbum != null && trackRepository.countByAlbumId(existingAlbum.getId()) > 0) {
                reconcileTracksWithDataset(existingAlbum);
                return existingAlbum;
            }
        }

        // 3. Check if album already exists for this artist with the same normalized title (prevents duplicate album rows across editions)
        if (existingAlbum == null && details.title() != null) {
            String normTitle = normalizeTitle(details.title());
            String artistMbId = details.artistCredit() != null && !details.artistCredit().isEmpty() && details.artistCredit().get(0).artist() != null
                    ? details.artistCredit().get(0).artist().id() : null;
            if (artistMbId != null) {
                Artist localArtist = artistRepository.findFirstByMusicBrainzIdOrderByIdAsc(artistMbId).orElse(null);
                if (localArtist != null) {
                    List<Album> artistAlbums = albumRepository.findByArtistId(localArtist.getId());
                    for (Album a : artistAlbums) {
                        if (a.getTitle() != null && normalizeTitle(a.getTitle()).equalsIgnoreCase(normTitle) && trackRepository.countByAlbumId(a.getId()) > 0) {
                            existingAlbum = a;
                            break;
                        }
                    }
                }
            }
            if (existingAlbum != null) {
                reconcileTracksWithDataset(existingAlbum);
                return existingAlbum;
            }
        }

        // Strictly reject empty releases with 0 tracks - never allow dead albums in the database
        int expectedTotalTracks = details.media() != null
                ? details.media().stream()
                        .filter(m -> m.tracks() != null)
                        .mapToInt(m -> m.tracks().size())
                        .sum()
                : 0;
        if (expectedTotalTracks <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot sync empty release: this release has 0 tracks listed on MusicBrainz."
            );
        }

        Album album = existingAlbum != null ? existingAlbum : createAlbum(details, canonicalReleaseId);
        if (album.getMusicBrainzId() == null || !album.getMusicBrainzId().equals(canonicalReleaseId)) {
            album.setMusicBrainzId(canonicalReleaseId);
            album = albumRepository.save(album);
        }

        if (album.getCoverArtUrl() == null) {
            String releaseGroupId = details.releaseGroup() != null ? details.releaseGroup().id() : null;
            String coverUrl = coverArtClient.findFrontCover(canonicalReleaseId, releaseGroupId);
            if (coverUrl != null && coverUrl.startsWith("http://")) {
                coverUrl = "https://" + coverUrl.substring(7);
            }
            album.setCoverArtUrl(coverUrl);
            album = albumRepository.save(album);
        }

        syncTracks(details, album);
        reconcileTracksWithDataset(album);
        return album;
    }

    public List<FeedReleaseResponse> getPersonalizedFeed(String username, int limit) {
        int targetLimit = (limit <= 0 || limit > 20) ? 12 : limit;
        if (username == null || username.isBlank() || userAlbumHistoryRepository == null) {
            return Collections.emptyList();
        }

        List<UserAlbumHistory> historyList =
                userAlbumHistoryRepository.findByUsernameIgnoreCaseOrderByPlayedAtDesc(username);

        if (historyList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> playedAlbumIds = new HashSet<>();
        Set<String> playedTitles = new HashSet<>();
        // Maintain artists in order of user's most recent interaction
        Map<Long, Artist> userArtists = new LinkedHashMap<>();
        Map<Long, String> lastPlayedAlbumTitleByArtist = new HashMap<>();

        for (UserAlbumHistory h : historyList) {
            if (h.getAlbum() != null) {
                playedAlbumIds.add(h.getAlbum().getId());
                if (h.getAlbum().getTitle() != null) {
                    playedTitles.add(normalizeTitle(h.getAlbum().getTitle()));
                }
                Artist artist = h.getAlbum().getArtist();
                if (artist != null && artist.getId() != null) {
                    userArtists.putIfAbsent(artist.getId(), artist);
                    lastPlayedAlbumTitleByArtist.putIfAbsent(artist.getId(), h.getAlbum().getTitle());
                }
            }
        }

        List<FeedReleaseResponse> feed = new ArrayList<>();
        Set<String> includedMbids = new HashSet<>();
        Set<String> includedTitles = new HashSet<>(playedTitles);

        // Priority 1: Other streamable albums in catalog_db by the artists the user has synced/listened to
        for (Artist artist : userArtists.values()) {
            List<Album> artistAlbums = albumRepository.findByArtistId(artist.getId());
            for (Album album : artistAlbums) {
                // Strictly filter by albums: must have at least 5 tracks in database (exclude singles & promo tracks)
                long trackCount = trackRepository.countByAlbumId(album.getId());
                if (trackCount < 5) {
                    continue;
                }
                String normTitle = normalizeTitle(album.getTitle());
                if (!playedAlbumIds.contains(album.getId()) && !playedTitles.contains(normTitle) && includedTitles.add(normTitle) && includedMbids.add(album.getMusicBrainzId())) {
                    String reason = "Because you listened to " + lastPlayedAlbumTitleByArtist.get(artist.getId());
                    feed.add(FeedReleaseResponse.fromAlbum(album, (int) trackCount, reason));
                    if (feed.size() >= targetLimit) {
                        return feed;
                    }
                }
            }
        }

        // Priority 2: Notable official releases from MusicBrainz for these artists not yet in catalog
        if (feed.size() < targetLimit) {
            for (Artist artist : userArtists.values()) {
                if (artist.getMusicBrainzId() == null || artist.getMusicBrainzId().isBlank()) {
                    continue;
                }
                try {
                    MusicBrainzReleaseSearchResponse mbReleases =
                            musicBrainzClient.getArtistReleases(artist.getMusicBrainzId());
                    if (mbReleases != null && mbReleases.releases() != null) {
                        int addedForArtist = 0;
                        for (MusicBrainzRelease rel : mbReleases.releases()) {
                            int trackCount = rel.trackCount() != null ? rel.trackCount() : 0;
                            // Strictly filter to full albums: must have at least 5 tracks (never unsynced individual tracks or singles)
                            if (trackCount < 5 || rel.title() == null || rel.title().isBlank()) {
                                continue;
                            }
                            if (rel.releaseGroup() != null && rel.releaseGroup().primaryType() != null
                                    && !"Album".equalsIgnoreCase(rel.releaseGroup().primaryType())) {
                                continue;
                            }
                            String normTitle = normalizeTitle(rel.title());
                            if (!playedTitles.contains(normTitle) && includedTitles.add(normTitle) && includedMbids.add(rel.id())) {
                                String reason = "From your library artist: " + artist.getName();
                                feed.add(FeedReleaseResponse.fromMusicBrainz(
                                        rel,
                                        artist.getName(),
                                        artist.getMusicBrainzId(),
                                        reason
                                ));
                                addedForArtist++;
                                if (feed.size() >= targetLimit) {
                                    return feed;
                                }
                                if (addedForArtist >= 2) {
                                    break; // Max 2 external releases per artist to keep balance
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Error retrieving artist releases for feed [{}]: {}", artist.getName(), ex.getMessage());
                }
            }
        }

        return feed;
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) return "";
        return title.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    @Transactional
    public Album resolveMissingCoverArt(Album album) {
        if (album == null || album.getCoverArtUrl() != null || album.getMusicBrainzId() == null) {
            return album;
        }
        try {
            MusicBrainzReleaseDetails details = musicBrainzClient.getReleaseDetails(album.getMusicBrainzId());
            String releaseGroupId = details != null && details.releaseGroup() != null ? details.releaseGroup().id() : null;
            String coverUrl = coverArtClient.findFrontCover(album.getMusicBrainzId(), releaseGroupId);
            if (coverUrl != null) {
                if (coverUrl.startsWith("http://")) {
                    coverUrl = "https://" + coverUrl.substring(7);
                }
                album.setCoverArtUrl(coverUrl);
                return albumRepository.save(album);
            }
        } catch (Exception e) {
            log.warn("Failed to resolve missing cover art for album {}: {}", album.getId(), e.getMessage());
        }
        return album;
    }

    private Album createAlbum(
            MusicBrainzReleaseDetails details,
            String musicBrainzId
    ) {
        String artistName = "Unknown Artist";
        String artistMbId = null;

        if (details.artistCredit() != null && !details.artistCredit().isEmpty()) {
            MusicBrainzArtistCredit credit = details.artistCredit().get(0);
            if (credit.name() != null && !credit.name().isBlank()) {
                artistName = credit.name();
            } else if (credit.artist() != null && credit.artist().name() != null) {
                artistName = credit.artist().name();
            }
            if (credit.artist() != null) {
                artistMbId = credit.artist().id();
            }
        }

        final String finalArtistName = artistName;
        final String finalArtistMbId = artistMbId;
        Artist artist;
        if (finalArtistMbId != null) {
            artist = artistRepository.findFirstByMusicBrainzIdOrderByIdAsc(finalArtistMbId)
                    .orElseGet(() -> artistRepository.save(new Artist(finalArtistName, finalArtistMbId)));
        } else {
            artist = artistRepository.findFirstByNameOrderByIdAsc(finalArtistName)
                    .orElseGet(() -> artistRepository.save(new Artist(finalArtistName, null)));
        }

        String title = details.title() != null ? details.title() : "Untitled Album";
        return albumRepository.save(new Album(
                title,
                musicBrainzId,
                artist
        ));
    }

    private void syncTracks(
            MusicBrainzReleaseDetails details,
            Album album
    ) {
        if (details.media() == null) {
            return;
        }

        List<Track> existingTracks = trackRepository.findByAlbumIdOrderByPosition(album.getId());
        int expectedTotalTracks = details.media().stream()
                .filter(m -> m.tracks() != null)
                .mapToInt(m -> m.tracks().size())
                .sum();

        // If album already has the expected complete tracklist, nothing to do
        if (!existingTracks.isEmpty() && existingTracks.size() == expectedTotalTracks) {
            return;
        }

        // If tracks are missing or incomplete (e.g. from previous corrupted sync), clean them up
        if (!existingTracks.isEmpty()) {
            trackRepository.deleteAll(existingTracks);
        }

        int position = 1;
        List<Track> tracksToSave = new ArrayList<>();

        for (MusicBrainzMedia media : details.media()) {
            if (media.tracks() == null) {
                continue;
            }

            for (MusicBrainzTrack musicBrainzTrack : media.tracks()) {
                MusicBrainzRecording recording = musicBrainzTrack.recording();

                // Prefer recording ID (which holds url-rels in MusicBrainz), fallback to track ID or position
                String trackMusicBrainzId = recording != null && recording.id() != null
                        ? recording.id()
                        : (musicBrainzTrack.id() != null && !musicBrainzTrack.id().isBlank()
                                ? musicBrainzTrack.id()
                                : album.getMusicBrainzId() + "-trk-" + position);

                String title = recording != null && recording.title() != null && !recording.title().isBlank()
                        ? recording.title()
                        : musicBrainzTrack.title();
                if (title == null || title.isBlank()) {
                    title = "Track " + position;
                }

                Integer length = recording != null && recording.length() != null
                        ? recording.length()
                        : musicBrainzTrack.length();

                tracksToSave.add(new Track(
                        trackMusicBrainzId,
                        title,
                        position,
                        length,
                        album
                ));

                position++;
            }
        }

        if (!tracksToSave.isEmpty()) {
            trackRepository.saveAll(tracksToSave);
        }
    }

    public String cleanTrackTitle(String title) {
        if (title == null || title.isBlank()) return "Untitled Track";
        String s = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD);
        s = s.replaceAll("\\p{M}", "");
        s = s.replaceAll("[‐–—]", "-");
        s = s.replaceAll("['’`]", "'");
        s = s.replaceAll("(?i)\\s*[\\[\\(].*?(remaster|deluxe|bonus|version|edition|mono|stereo|anniversary|feat\\.|ft\\.).*?[\\]\\)]", "");
        s = s.replaceAll("(?i)\\s*-\\s*(remaster(ed)?|deluxe|bonus track).*$", "");
        s = s.trim();
        return s.isEmpty() ? title.trim() : s;
    }

    public String cleanAlbumTitle(String title) {
        return cleanAlbumTitleStatic(title);
    }

    public static String cleanAlbumTitleStatic(String title) {
        if (title == null || title.isBlank()) return "";
        String s = title.replaceAll("(?i)[\\(\\[\\{].*?(anniversary|deluxe|remaster|expanded|edition|reissue|bonus|special|box\\s*set|version|taylor's\\s*version).*?[\\)\\]\\}]", "");
        s = s.replaceAll("(?i)\\s*-\\s*(anniversary|deluxe|remaster|expanded|edition|reissue|bonus|special|version).*", "");
        return s.trim();
    }

    public static boolean isAlbumMatch(String title1, String title2) {
        if (title1 == null || title2 == null) return false;
        String c1 = cleanAlbumTitleStatic(title1).toLowerCase().trim();
        String c2 = cleanAlbumTitleStatic(title2).toLowerCase().trim();
        if (c1.equalsIgnoreCase(c2)) return true;

        String a1 = normalizeAlphaNumeric(c1);
        String a2 = normalizeAlphaNumeric(c2);
        if (!a1.isEmpty() && a1.equals(a2)) return true;

        String s1 = a1.startsWith("the") && a1.length() > 3 ? a1.substring(3) : a1;
        String s2 = a2.startsWith("the") && a2.length() > 3 ? a2.substring(3) : a2;
        if (!s1.isEmpty() && s1.equals(s2)) return true;

        if (a1.length() >= 4 && a2.length() >= 4) {
            if (a1.startsWith(a2) || a2.startsWith(a1) || a1.contains(a2) || a2.contains(a1)) return true;
        }
        if (s1.length() >= 4 && s2.length() >= 4) {
            if (s1.startsWith(s2) || s2.startsWith(s1) || s1.contains(s2) || s2.contains(s1)) return true;
        }
        return false;
    }

    private static String normalizeAlphaNumeric(String str) {
        if (str == null) return "";
        return str.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    public Map<String, Object> findBestCatalogMatch(String targetCleanTitle, List<Map<String, Object>> catalogRows) {
        return findBestCatalogMatch(targetCleanTitle, null, catalogRows);
    }

    public Map<String, Object> findBestCatalogMatch(String targetCleanTitle, Integer targetLengthMs, List<Map<String, Object>> catalogRows) {
        if (targetCleanTitle == null || targetCleanTitle.isBlank() || catalogRows == null) return null;
        String tLower = targetCleanTitle.trim().toLowerCase();

        for (Map<String, Object> row : catalogRows) {
            String raw = (String) row.get("title");
            String c = cleanTrackTitle(raw).toLowerCase();
            if (c.equals(tLower)) {
                return row;
            }
        }

        for (Map<String, Object> row : catalogRows) {
            String raw = (String) row.get("title");
            String c = cleanTrackTitle(raw).toLowerCase();
            if (c.startsWith(tLower) || tLower.startsWith(c)) {
                return row;
            }
        }

        for (Map<String, Object> row : catalogRows) {
            String raw = (String) row.get("title");
            String c = cleanTrackTitle(raw).toLowerCase();
            if (c.contains(tLower) || tLower.contains(c)) {
                return row;
            }
        }

        for (Map<String, Object> row : catalogRows) {
            String raw = (String) row.get("title");
            Integer dur = row.get("duration") != null ? ((Number) row.get("duration")).intValue() : null;
            if (CatalogNormalizationUtils.isTrackFuzzyMatch(targetCleanTitle, targetLengthMs, raw, dur)) {
                return row;
            }
        }

        return null;
    }

    @Transactional
    public Album syncAlbumFromYoutubeCatalog(String syntheticId) {
        Album existing = albumRepository.findFirstByMusicBrainzIdOrderByIdAsc(syntheticId).orElse(null);
        if (existing != null) {
            return existing;
        }

        String encoded = syntheticId.substring(3);
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dataset release id: " + syntheticId);
        }

        String[] parts = decoded.split("::", 2);
        if (parts.length < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed dataset release id: " + syntheticId);
        }
        String artistName = parts[0];
        String albumTitle = parts[1];

        Artist artist = artistRepository.findFirstByNameOrderByIdAsc(artistName)
                .orElseGet(() -> artistRepository.save(new Artist(artistName, null)));

        Album album = new Album(albumTitle, syntheticId, artist);
        album = albumRepository.save(album);

        if (jdbcTemplate != null) {
            List<Map<String, Object>> rows = Collections.emptyList();
            try {
                String sql = "SELECT song_id, title, duration FROM youtube_catalog WHERE LOWER(artist) = LOWER(?) AND LOWER(album) = LOWER(?) ORDER BY title";
                rows = jdbcTemplate.queryForList(sql, artistName, albumTitle);
            } catch (Exception ex) {
                log.warn("Failed to query youtube_catalog for synthetic album [{}]: {}", albumTitle, ex.getMessage());
            }
            List<Track> tracksToSave = new ArrayList<>();
            int pos = 1;
            String firstSongId = null;

            for (Map<String, Object> row : rows) {
                String songId = (String) row.get("song_id");
                String rawTitle = (String) row.get("title");
                Integer duration = row.get("duration") != null ? ((Number) row.get("duration")).intValue() : null;
                if (firstSongId == null) {
                    firstSongId = songId;
                }
                String cleanTitle = cleanTrackTitle(rawTitle);
                Track t = new Track(
                        "yt-trk:" + songId,
                        cleanTitle,
                        pos++,
                        duration != null ? duration * 1000 : null,
                        album
                );
                t.setYoutubeVideoId(songId);
                tracksToSave.add(t);
            }

            if (!tracksToSave.isEmpty()) {
                trackRepository.saveAll(tracksToSave);
            }

            if (firstSongId != null) {
                album.setCoverArtUrl("https://img.youtube.com/vi/" + firstSongId + "/hqdefault.jpg");
                album = albumRepository.save(album);
            }
        }

        return album;
    }

    @Transactional
    public List<Map<String, Object>> fetchAlbumCatalogRows(String artistName, Set<String> artistVariants, String albumTitle) {
        if (jdbcTemplate == null || albumTitle == null || albumTitle.isBlank()) return Collections.emptyList();
        String cleanAlbum = cleanAlbumTitle(albumTitle);
        boolean isLive = CatalogNormalizationUtils.isLiveOrAcousticTitle(albumTitle);

        Set<String> allVariants = new LinkedHashSet<>();
        if (artistName != null && !artistName.isBlank()) allVariants.add(artistName.trim());
        if (artistVariants != null) allVariants.addAll(artistVariants);

        // 1. Direct equality check (fastest and backwards-compatible with standard mocks)
        String exactSql = "SELECT song_id, title, duration FROM youtube_catalog WHERE LOWER(artist) = LOWER(?) AND LOWER(album) = LOWER(?) ORDER BY title";
        for (String variant : allVariants) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(exactSql, variant, albumTitle);
                if (rows.isEmpty() && !cleanAlbum.equalsIgnoreCase(albumTitle)) {
                    rows = jdbcTemplate.queryForList(exactSql, variant, cleanAlbum);
                }
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
            ORDER BY (CASE WHEN LOWER(album) = LOWER(?) THEN 0 ELSE 1 END), title
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
                    if (isAlbumMatch(albumTitle, cand) || isAlbumMatch(cleanAlbum, cand)) {
                        matchedCandAlbums.add(cand);
                    }
                }
                if (!matchedCandAlbums.isEmpty()) {
                    StringBuilder inQuery = new StringBuilder("SELECT song_id, title, duration, album FROM youtube_catalog WHERE LOWER(artist) = LOWER(?) AND album IN (");
                    for (int i = 0; i < matchedCandAlbums.size(); i++) {
                        if (i > 0) inQuery.append(",");
                        inQuery.append("'").append(matchedCandAlbums.get(i).replace("'", "''")).append("'");
                    }
                    inQuery.append(") ORDER BY title");
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(inQuery.toString(), variant);
                    if (!rows.isEmpty()) {
                        return rows;
                    }
                }
            } catch (Exception ignored) {}
        }

        return Collections.emptyList();
    }

    public List<Track> reconcileTracksWithDataset(Album album) {
        if (album == null || jdbcTemplate == null) {
            return album != null ? trackRepository.findByAlbumIdOrderByPosition(album.getId()) : Collections.emptyList();
        }
        String artistName = album.getArtist() != null ? album.getArtist().getName() : null;
        String albumTitle = album.getTitle();
        if (artistName == null || artistName.isBlank() || albumTitle == null || albumTitle.isBlank()) {
            return trackRepository.findByAlbumIdOrderByPosition(album.getId());
        }

        List<Track> existingTracks = trackRepository.findByAlbumIdOrderByPosition(album.getId());

        List<Map<String, Object>> catalogRows = Collections.emptyList();
        try {
            // Gather artist variants: aliases sharing MusicBrainz ID + vowel/phonetic variants
            Set<String> artistVariants = new LinkedHashSet<>();
            artistVariants.addAll(CatalogNormalizationUtils.generateArtistVariants(artistName));
            if (album.getArtist() != null && album.getArtist().getMusicBrainzId() != null) {
                try {
                    List<Artist> mbidArtists = artistRepository.findAllByMusicBrainzId(album.getArtist().getMusicBrainzId());
                    for (Artist a : mbidArtists) {
                        if (a.getName() != null) {
                            artistVariants.addAll(CatalogNormalizationUtils.generateArtistVariants(a.getName()));
                        }
                    }
                } catch (Exception ignored) {}
            }

            catalogRows = fetchAlbumCatalogRows(artistName, artistVariants, albumTitle);
        } catch (Exception ex) {
            log.warn("Failed to query youtube_catalog for album [{}]: {}", albumTitle, ex.getMessage());
            return existingTracks;
        }

        if (catalogRows.isEmpty()) {
            return existingTracks;
        }

        boolean isLiveAlbum = CatalogNormalizationUtils.isLiveOrAcousticTitle(albumTitle);
        boolean hasSlashMedley = existingTracks.stream().anyMatch(t -> t.getTitle() != null && t.getTitle().contains(" / "));
        boolean hasMissingTracks = catalogRows.size() > existingTracks.size();
        boolean hasMissingVideoIds = existingTracks.stream().anyMatch(t -> t.getYoutubeVideoId() == null || t.getYoutubeVideoId().isBlank());
        boolean hasMismatches = isLiveAlbum || hasMissingVideoIds;

        if (!hasSlashMedley && !hasMissingTracks && !hasMissingVideoIds && !hasMismatches) {
            return existingTracks;
        }

        log.info("Reconciling tracklist for album [{}] by [{}] (DB tracks: {}, Catalog tracks: {})",
                albumTitle, artistName, existingTracks.size(), catalogRows.size());

        if (hasSlashMedley) {
            List<Track> medleysToDelete = new ArrayList<>();
            List<Track> updatedExisting = new ArrayList<>();
            List<Track> newTracksToAdd = new ArrayList<>();
            Set<String> includedSongIds = new HashSet<>();
            int position = 1;

            for (Track t : existingTracks) {
                String origTitle = t.getTitle() != null ? t.getTitle() : "";
                if (origTitle.contains(" / ")) {
                    String[] parts = origTitle.split("\\s*/\\s*");
                    boolean anySubMatch = false;
                    for (String part : parts) {
                        String cleanPart = cleanTrackTitle(part);
                        Map<String, Object> matchedCat = findBestCatalogMatch(cleanPart, null, catalogRows);
                        if (matchedCat != null) {
                            String songId = (String) matchedCat.get("song_id");
                            if (includedSongIds.add(songId)) {
                                String catTitle = cleanTrackTitle((String) matchedCat.get("title"));
                                Integer dur = matchedCat.get("duration") != null ? ((Number) matchedCat.get("duration")).intValue() : null;
                                Track newTrk = new Track("yt-trk:" + songId, catTitle, position++, dur != null ? dur * 1000 : null, album);
                                newTrk.setYoutubeVideoId(songId);
                                newTracksToAdd.add(newTrk);
                                anySubMatch = true;
                            }
                        }
                    }
                    if (anySubMatch) {
                        medleysToDelete.add(t);
                    } else {
                        t.setPosition(position++);
                        updatedExisting.add(t);
                    }
                } else {
                    String clean = cleanTrackTitle(origTitle);
                    Map<String, Object> matchedCat = findBestCatalogMatch(clean, t.getLengthMilliseconds(), catalogRows);
                    if (matchedCat != null) {
                        String songId = (String) matchedCat.get("song_id");
                        includedSongIds.add(songId);
                        if (t.getYoutubeVideoId() == null || t.getYoutubeVideoId().isBlank() || isLiveAlbum) {
                            t.setYoutubeVideoId(songId);
                        }
                    }
                    t.setPosition(position++);
                    updatedExisting.add(t);
                }
            }

            for (Map<String, Object> row : catalogRows) {
                String songId = (String) row.get("song_id");
                if (!includedSongIds.contains(songId)) {
                    String rawTitle = (String) row.get("title");
                    String cleanTitle = cleanTrackTitle(rawTitle);
                    Integer dur = row.get("duration") != null ? ((Number) row.get("duration")).intValue() : null;
                    Track extraTrk = new Track("yt-trk:" + songId, cleanTitle, position++, dur != null ? dur * 1000 : null, album);
                    extraTrk.setYoutubeVideoId(songId);
                    newTracksToAdd.add(extraTrk);
                    includedSongIds.add(songId);
                }
            }

            if (!medleysToDelete.isEmpty()) {
                trackRepository.deleteAll(medleysToDelete);
                trackRepository.flush();
            }
            if (!updatedExisting.isEmpty()) {
                trackRepository.saveAll(updatedExisting);
            }
            if (!newTracksToAdd.isEmpty()) {
                trackRepository.saveAll(newTracksToAdd);
            }
            return trackRepository.findByAlbumIdOrderByPosition(album.getId());
        } else {
            Set<String> matchedSongIds = new HashSet<>();
            for (Track t : existingTracks) {
                String clean = cleanTrackTitle(t.getTitle());
                Map<String, Object> matchedCat = findBestCatalogMatch(clean, t.getLengthMilliseconds(), catalogRows);
                if (matchedCat != null) {
                    String songId = (String) matchedCat.get("song_id");
                    matchedSongIds.add(songId);
                    if (t.getYoutubeVideoId() == null || t.getYoutubeVideoId().isBlank() || isLiveAlbum) {
                        t.setYoutubeVideoId(songId);
                    }
                }
            }

            int nextPos = existingTracks.size() + 1;
            List<Track> toAdd = new ArrayList<>();
            for (Map<String, Object> row : catalogRows) {
                String songId = (String) row.get("song_id");
                if (!matchedSongIds.contains(songId)) {
                    String cleanTitle = cleanTrackTitle((String) row.get("title"));
                    Integer dur = row.get("duration") != null ? ((Number) row.get("duration")).intValue() : null;
                    boolean alreadyPresent = existingTracks.stream()
                            .anyMatch(et -> cleanTrackTitle(et.getTitle()).equalsIgnoreCase(cleanTitle)
                                    || CatalogNormalizationUtils.isTrackFuzzyMatch(et.getTitle(), et.getLengthMilliseconds(), cleanTitle, dur));
                    if (!alreadyPresent) {
                        Track extra = new Track("yt-trk:" + songId, cleanTitle, nextPos++, dur != null ? dur * 1000 : null, album);
                        extra.setYoutubeVideoId(songId);
                        toAdd.add(extra);
                        matchedSongIds.add(songId);
                    }
                }
            }

            trackRepository.saveAll(existingTracks);
            if (!toAdd.isEmpty()) {
                trackRepository.saveAll(toAdd);
            }
            return trackRepository.findByAlbumIdOrderByPosition(album.getId());
        }
    }

    public MusicBrainzReleaseSearchResponse getArtistReleasesWithDataset(String artistId) {
        MusicBrainzReleaseSearchResponse mbResponse = null;
        String artistName = null;

        if (artistId != null && artistId.startsWith("yt-art-")) {
            try {
                artistName = new String(Base64.getUrlDecoder().decode(artistId.substring(7)), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        } else {
            try {
                mbResponse = musicBrainzClient.getArtistReleases(artistId);
            } catch (Exception ex) {
                log.warn("MusicBrainz artist release lookup failed for [{}]: {}", artistId, ex.getMessage());
            }

            try {
                MusicBrainzArtist details = musicBrainzClient.getArtistDetails(artistId);
                if (details != null && details.name() != null) {
                    artistName = details.name();
                }
            } catch (Exception ex) {
                log.debug("Failed to fetch artist details for MBID {}: {}", artistId, ex.getMessage());
            }

            if (artistName == null) {
                Artist localArtist = artistRepository.findFirstByMusicBrainzIdOrderByIdAsc(artistId).orElse(null);
                if (localArtist != null) {
                    artistName = localArtist.getName();
                }
            }
        }

        if (jdbcTemplate == null || artistName == null || artistName.isBlank()) {
            return mbResponse != null ? mbResponse : new MusicBrainzReleaseSearchResponse(0, Collections.emptyList());
        }

        // 1. Fetch all albums from youtube_catalog for this artist and aliases
        Set<String> artistVariants = new LinkedHashSet<>();
        artistVariants.addAll(CatalogNormalizationUtils.generateArtistVariants(artistName));
        if (artistId != null && !artistId.startsWith("yt-")) {
            try {
                List<Artist> mbidArtists = artistRepository.findAllByMusicBrainzId(artistId);
                for (Artist a : mbidArtists) {
                    if (a.getName() != null) {
                        artistVariants.addAll(CatalogNormalizationUtils.generateArtistVariants(a.getName()));
                    }
                }
            } catch (Exception ignored) {}
        }
        try {
            String catArtistSql = "SELECT DISTINCT artist FROM youtube_catalog WHERE LOWER(artist) LIKE LOWER(?) OR LOWER(artist) LIKE LOWER(?)";
            String prefix = artistName.length() >= 4 ? artistName.substring(0, 4) + "%" : artistName + "%";
            List<String> foundCatArtists = jdbcTemplate.query(catArtistSql, (rs, rowNum) -> rs.getString("artist"), artistName, prefix);
            for (String fca : foundCatArtists) {
                if (fca != null && CatalogNormalizationUtils.isArtistMatch(artistName, fca)) {
                    artistVariants.add(fca);
                }
            }
        } catch (Exception ignored) {}

        List<Map<String, Object>> catalogRows = new ArrayList<>();
        Set<String> seenCatalogAlbums = new HashSet<>();
        for (String varArtist : artistVariants) {
            String sql = "SELECT album, count(*) as track_count FROM youtube_catalog WHERE LOWER(artist) = LOWER(?) GROUP BY album";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, varArtist);
            for (Map<String, Object> row : rows) {
                String album = (String) row.get("album");
                if (album != null && !album.isBlank() && seenCatalogAlbums.add(album.toLowerCase().trim())) {
                    catalogRows.add(row);
                }
            }
        }
        Map<String, Map<String, Object>> catalogMap = new LinkedHashMap<>();
        for (Map<String, Object> row : catalogRows) {
            String album = (String) row.get("album");
            if (album != null && !album.isBlank()) {
                catalogMap.put(album, row);
            }
        }

        // 2. Fetch local synced albums for this artist in Aficio database
        Set<String> localSyncedAlbumTitles = new HashSet<>();
        List<Album> localAlbumsList = new ArrayList<>();
        artistRepository.findFirstByMusicBrainzIdOrderByIdAsc(artistId).ifPresent(a -> {
            List<Album> existing = albumRepository.findByArtistId(a.getId());
            for (Album alb : existing) {
                if (alb.getTitle() != null && !alb.getTitle().isBlank()) {
                    localSyncedAlbumTitles.add(alb.getTitle());
                    localAlbumsList.add(alb);
                }
            }
        });

        List<MusicBrainzRelease> combined = new ArrayList<>();
        Set<String> matchedCatalogAlbumNames = new HashSet<>();
        Set<String> seenNormTitles = new HashSet<>();

        // Priority 1: Add canonical local synced albums in Aficio DB (ensures official albums like folklore and evermore are always prioritized)
        for (Album alb : localAlbumsList) {
            if (alb.getMusicBrainzId() != null && !alb.getMusicBrainzId().startsWith("yt-")) {
                String norm = normalizeTitle(alb.getTitle());
                if (seenNormTitles.add(norm)) {
                    int trackCount = (int) trackRepository.countByAlbumId(alb.getId());
                    MusicBrainzReleaseGroup rg = new MusicBrainzReleaseGroup(
                            alb.getMusicBrainzId(),
                            alb.getTitle(),
                            "Album",
                            null,
                            null,
                            null,
                            null
                    );
                    MusicBrainzRelease rel = new MusicBrainzRelease(
                            alb.getMusicBrainzId(),
                            alb.getTitle(),
                            null,
                            "XW",
                            "Official",
                            100,
                            trackCount,
                            List.of(new MusicBrainzArtistCredit(artistName, new MusicBrainzArtistReference(artistId, artistName))),
                            rg
                    );
                    combined.add(rel);
                    matchedCatalogAlbumNames.add(alb.getTitle());
                }
            }
        }

        // 3. Filter MusicBrainz releases: ONLY populate and make available releases available in dataset or local DB
        if (mbResponse != null && mbResponse.releases() != null) {
            for (MusicBrainzRelease rel : mbResponse.releases()) {
                if (rel == null || rel.title() == null || rel.title().isBlank()) continue;

                String matchedCatalogAlbum = findCatalogAlbumMatch(rel.title(), catalogMap.keySet(), localSyncedAlbumTitles);
                if (matchedCatalogAlbum != null) {
                    String norm = normalizeTitle(rel.title());
                    if (seenNormTitles.add(norm)) {
                        combined.add(rel);
                        if (!matchedCatalogAlbum.isEmpty()) {
                            matchedCatalogAlbumNames.add(matchedCatalogAlbum);
                        }
                    }
                }
            }
        }

        // 4. Add dataset-exclusive releases present in youtube_catalog that were not covered by MusicBrainz
        for (Map.Entry<String, Map<String, Object>> entry : catalogMap.entrySet()) {
            String catAlbum = entry.getKey();
            if (!matchedCatalogAlbumNames.contains(catAlbum)) {
                String norm = normalizeTitle(catAlbum);
                if (seenNormTitles.add(norm)) {
                    Map<String, Object> row = entry.getValue();
                    int trackCount = row.get("track_count") != null ? ((Number) row.get("track_count")).intValue() : 1;
                    String syntheticId = "yt-" + Base64.getUrlEncoder().withoutPadding().encodeToString((artistName + "::" + catAlbum).getBytes(StandardCharsets.UTF_8));
                    String pType = trackCount >= 5 ? "Album" : (trackCount >= 2 ? "EP" : "Single");

                    MusicBrainzReleaseGroup rg = new MusicBrainzReleaseGroup(
                            null,
                            catAlbum,
                            pType,
                            null,
                            null,
                            null,
                            null
                    );
                    MusicBrainzRelease rel = new MusicBrainzRelease(
                            syntheticId,
                            catAlbum,
                            null,
                            "XW",
                            "Official",
                            95,
                            trackCount,
                            List.of(new MusicBrainzArtistCredit(artistName, new MusicBrainzArtistReference(artistId, artistName))),
                            rg
                    );
                    combined.add(rel);
                }
            }
        }

        return new MusicBrainzReleaseSearchResponse(combined.size(), combined);
    }

    private String findCatalogAlbumMatch(String mbTitle, Set<String> catalogAlbumNames, Set<String> localSyncedAlbumTitles) {
        if (mbTitle == null || mbTitle.isBlank()) return null;

        for (String local : localSyncedAlbumTitles) {
            if (isAlbumMatch(mbTitle, local)) {
                return "";
            }
        }

        for (String cat : catalogAlbumNames) {
            if (isAlbumMatch(mbTitle, cat)) {
                return cat;
            }
        }

        return null;
    }

    public MusicBrainzReleaseSearchResponse searchReleasesWithDataset(String query) {
        if (query == null || query.isBlank()) {
            return new MusicBrainzReleaseSearchResponse(0, Collections.emptyList());
        }

        MusicBrainzReleaseSearchResponse mbResponse = null;
        try {
            mbResponse = musicBrainzClient.searchReleases(query);
        } catch (Exception ex) {
            log.warn("MusicBrainz release search failed for [{}]: {}", query, ex.getMessage());
        }

        List<MusicBrainzRelease> combined = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        Set<String> coveredCatalogKeys = new HashSet<>();

        // 1. Fetch matching albums from youtube_catalog (case-insensitive, substring, and multi-word resilient)
        List<Map<String, Object>> catalogRows = Collections.emptyList();
        if (jdbcTemplate != null) {
            String cleanQuery = query.trim().replaceAll("\\s+", " ");
            String lowerQuery = cleanQuery.toLowerCase();
            String likePattern = "%" + lowerQuery + "%";
            String prefixPattern = lowerQuery + "%";
            String altLikePattern = lowerQuery.startsWith("the ")
                    ? "%" + lowerQuery.substring(4).trim() + "%"
                    : "%the " + lowerQuery + "%";

            String sql = """
                SELECT artist, album, count(*) as track_count
                FROM youtube_catalog
                WHERE LOWER(album) LIKE ?
                   OR LOWER(artist) LIKE ?
                   OR (LOWER(artist) || ' ' || LOWER(album)) LIKE ?
                   OR (LOWER(album) || ' ' || LOWER(artist)) LIKE ?
                   OR LOWER(album) LIKE ?
                   OR LOWER(artist) LIKE ?
                GROUP BY artist, album
                ORDER BY
                  CASE
                    WHEN LOWER(album) = ? THEN 0
                    WHEN LOWER(artist) = ? THEN 1
                    WHEN LOWER(album) LIKE ? THEN 2
                    WHEN LOWER(artist) LIKE ? THEN 3
                    WHEN LOWER(album) LIKE ? THEN 4
                    ELSE 5
                  END,
                  count(*) DESC
                LIMIT 50
            """;
            try {
                catalogRows = jdbcTemplate.queryForList(
                        sql,
                        likePattern, likePattern, likePattern, likePattern, altLikePattern, altLikePattern,
                        lowerQuery, lowerQuery, prefixPattern, prefixPattern, likePattern
                );
            } catch (Exception ex) {
                log.warn("youtube_catalog album search failed for [{}]: {}", query, ex.getMessage());
            }

            // Multi-token fallback: if few results, match rows where every word is present in artist + album
            String[] tokens = lowerQuery.replaceAll("[^a-z0-9\\s]", " ").trim().split("\\s+");
            if (catalogRows.size() < 10 && tokens.length >= 2) {
                StringBuilder tokenSql = new StringBuilder("""
                    SELECT artist, album, count(*) as track_count
                    FROM youtube_catalog
                    WHERE 
                """);
                List<Object> params = new ArrayList<>();
                for (int i = 0; i < tokens.length; i++) {
                    if (tokens[i].isBlank()) continue;
                    if (!params.isEmpty()) tokenSql.append(" AND ");
                    tokenSql.append("(LOWER(artist) || ' ' || LOWER(album)) LIKE ?");
                    params.add("%" + tokens[i] + "%");
                }
                tokenSql.append(" GROUP BY artist, album ORDER BY count(*) DESC LIMIT 30");

                if (!params.isEmpty()) {
                    try {
                        List<Map<String, Object>> tokenRows = jdbcTemplate.queryForList(tokenSql.toString(), params.toArray());
                        Set<String> existingKeys = new HashSet<>();
                        for (Map<String, Object> r : catalogRows) {
                            existingKeys.add(String.valueOf(r.get("artist")).toLowerCase() + "::" + String.valueOf(r.get("album")).toLowerCase());
                        }
                        List<Map<String, Object>> merged = new ArrayList<>(catalogRows);
                        for (Map<String, Object> tr : tokenRows) {
                            String k = String.valueOf(tr.get("artist")).toLowerCase() + "::" + String.valueOf(tr.get("album")).toLowerCase();
                            if (existingKeys.add(k)) {
                                merged.add(tr);
                            }
                        }
                        catalogRows = merged;
                    } catch (Exception ex) {
                        log.debug("youtube_catalog tokenized search fallback failed for [{}]: {}", query, ex.getMessage());
                    }
                }
            }
        }

        // 2. Fetch any matching local synced albums in DB
        List<Album> localAlbums = Collections.emptyList();
        try {
            localAlbums = albumRepository.findByTitleContainingIgnoreCase(query.trim());
        } catch (Exception ignored) {}

        // 3. For each MusicBrainz search result, ONLY keep it if it is available in youtube_catalog or local DB
        if (mbResponse != null && mbResponse.releases() != null) {
            for (MusicBrainzRelease rel : mbResponse.releases()) {
                if (rel == null || rel.title() == null || rel.title().isBlank()) continue;
                String aName = rel.artistCredit() != null && !rel.artistCredit().isEmpty()
                        ? rel.artistCredit().get(0).name() : "";

                boolean matched = false;
                for (Album la : localAlbums) {
                    if (isAlbumMatch(rel.title(), la.getTitle())) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    for (Map<String, Object> crow : catalogRows) {
                        String cArtist = (String) crow.get("artist");
                        String cAlbum = (String) crow.get("album");
                        if (cAlbum != null && isAlbumMatch(rel.title(), cAlbum)) {
                            if (aName.isBlank() || cArtist == null || aName.equalsIgnoreCase(cArtist) || isAlbumMatch(aName, cArtist)) {
                                matched = true;
                                coveredCatalogKeys.add(normalizeTitle(cArtist) + "::" + normalizeTitle(cAlbum));
                                break;
                            }
                        }
                    }
                }

                if (matched) {
                    String key = normalizeTitle(aName) + "::" + normalizeTitle(rel.title());
                    if (seenKeys.add(key)) {
                        combined.add(rel);
                    }
                }
            }
        }

        // 4. For any catalog rows not covered by MusicBrainz releases, add synthetic releases
        for (Map<String, Object> row : catalogRows) {
            String artist = (String) row.get("artist");
            String album = (String) row.get("album");
            if (artist == null || album == null || album.isBlank()) continue;

            String key = normalizeTitle(artist) + "::" + normalizeTitle(album);
            if (!coveredCatalogKeys.contains(key) && seenKeys.add(key)) {
                int trackCount = row.get("track_count") != null ? ((Number) row.get("track_count")).intValue() : 1;
                String syntheticId = "yt-" + Base64.getUrlEncoder().withoutPadding().encodeToString((artist + "::" + album).getBytes(StandardCharsets.UTF_8));
                String pType = trackCount >= 5 ? "Album" : (trackCount >= 2 ? "EP" : "Single");

                MusicBrainzReleaseGroup rg = new MusicBrainzReleaseGroup(
                        null,
                        album,
                        pType,
                        null,
                        null,
                        null,
                        null
                );
                MusicBrainzRelease rel = new MusicBrainzRelease(
                        syntheticId,
                        album,
                        null,
                        "XW",
                        "Official",
                        95,
                        trackCount,
                        List.of(new MusicBrainzArtistCredit(artist, new MusicBrainzArtistReference(null, artist))),
                        rg
                );
                if (query.trim().equalsIgnoreCase(album) || query.trim().equalsIgnoreCase(artist)) {
                    combined.add(0, rel);
                } else {
                    combined.add(rel);
                }
            }
        }

        return new MusicBrainzReleaseSearchResponse(combined.size(), combined);
    }

    public MusicBrainzSearchResponse searchArtistsWithDataset(String query) {
        if (query == null || query.isBlank()) {
            return new MusicBrainzSearchResponse(0, Collections.emptyList());
        }

        MusicBrainzSearchResponse mbResponse = null;
        try {
            mbResponse = musicBrainzClient.searchArtists(query);
        } catch (Exception ex) {
            log.warn("MusicBrainz artist search failed for [{}]: {}", query, ex.getMessage());
        }

        List<MusicBrainzArtist> filtered = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        if (jdbcTemplate != null) {
            // Check MusicBrainz artists: ONLY keep those who have tracks in youtube_catalog or local DB
            if (mbResponse != null && mbResponse.artists() != null) {
                for (MusicBrainzArtist a : mbResponse.artists()) {
                    if (a == null || a.name() == null || a.name().isBlank()) continue;
                    if (isArtistAvailable(a.name())) {
                        if (seenNames.add(a.name().toLowerCase().trim())) {
                            filtered.add(a);
                        }
                    }
                }
            }

            // Also check youtube_catalog directly for matching artists (substring / prefix / exact / the-resilient)
            String cleanQuery = query.trim().replaceAll("\\s+", " ");
            String lowerQuery = cleanQuery.toLowerCase();
            String likePattern = "%" + lowerQuery + "%";
            String prefixPattern = lowerQuery + "%";
            String altLikePattern = lowerQuery.startsWith("the ")
                    ? "%" + lowerQuery.substring(4).trim() + "%"
                    : "%the " + lowerQuery + "%";

            String sql = """
                SELECT DISTINCT artist
                FROM youtube_catalog
                WHERE LOWER(artist) LIKE ?
                   OR LOWER(artist) LIKE ?
                ORDER BY
                  CASE
                    WHEN LOWER(artist) = ? THEN 0
                    WHEN LOWER(artist) LIKE ? THEN 1
                    WHEN LOWER(artist) LIKE ? THEN 2
                    ELSE 3
                  END,
                  artist ASC
                LIMIT 25
            """;
            try {
                List<String> catArtists = jdbcTemplate.query(
                        sql,
                        (rs, rowNum) -> rs.getString("artist"),
                        likePattern, altLikePattern,
                        lowerQuery, prefixPattern, likePattern
                );
                for (String catArtist : catArtists) {
                    if (catArtist != null && !catArtist.isBlank()) {
                        if (seenNames.add(catArtist.toLowerCase().trim())) {
                            String syntheticId = "yt-art-" + Base64.getUrlEncoder().withoutPadding()
                                    .encodeToString(catArtist.getBytes(StandardCharsets.UTF_8));
                            filtered.add(new MusicBrainzArtist(
                                    syntheticId,
                                    catArtist,
                                    null,
                                    "Dataset Catalog Artist",
                                    "Person/Group"
                            ));
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("youtube_catalog artist search failed for [{}]: {}", query, ex.getMessage());
            }

            return new MusicBrainzSearchResponse(filtered.size(), filtered);
        }

        return mbResponse != null ? mbResponse : new MusicBrainzSearchResponse(0, Collections.emptyList());
    }

    public boolean isArtistAvailable(String artistName) {
        if (artistName == null || artistName.isBlank()) return false;
        if (jdbcTemplate != null) {
            try {
                String sql = "SELECT 1 FROM youtube_catalog WHERE LOWER(artist) = LOWER(?) LIMIT 1";
                List<Integer> list = jdbcTemplate.query(sql, (rs, rowNum) -> 1, artistName.trim());
                if (!list.isEmpty()) {
                    return true;
                }
                Set<String> variants = CatalogNormalizationUtils.generateArtistVariants(artistName);
                for (String v : variants) {
                    if (v.equalsIgnoreCase(artistName)) continue;
                    List<Integer> vList = jdbcTemplate.query(sql, (rs, rowNum) -> 1, v.trim());
                    if (!vList.isEmpty()) {
                        return true;
                    }
                }
            } catch (Exception ex) {
                log.debug("isArtistAvailable check failed for [{}]: {}", artistName, ex.getMessage());
            }
        }
        try {
            return artistRepository.findFirstByNameOrderByIdAsc(artistName.trim())
                    .map(a -> !albumRepository.findByArtistId(a.getId()).isEmpty())
                    .orElse(false);
        } catch (Exception ex) {
            return false;
        }
    }

}

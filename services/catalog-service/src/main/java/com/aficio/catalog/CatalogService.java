package com.aficio.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
        MusicBrainzReleaseDetails details = musicBrainzClient.getReleaseDetails(musicBrainzId);
        if (details == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found on MusicBrainz for id: " + musicBrainzId);
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

        Album album = albumRepository.findFirstByMusicBrainzIdOrderByIdAsc(musicBrainzId)
                .orElseGet(() -> createAlbum(details, musicBrainzId));

        if (album.getCoverArtUrl() == null) {
            String releaseGroupId = details.releaseGroup() != null ? details.releaseGroup().id() : null;
            String coverUrl = coverArtClient.findFrontCover(musicBrainzId, releaseGroupId);
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
        if (title == null || title.isBlank()) return "";
        String s = title.replaceAll("(?i)[\\(\\[\\{].*?(anniversary|deluxe|remaster|expanded|edition|reissue|bonus|special|box\\s*set).*?[\\)\\]\\}]", "");
        s = s.replaceAll("(?i)\\s*-\\s*(anniversary|deluxe|remaster|expanded|edition|reissue|bonus|special).*", "");
        return s.trim();
    }

    private Map<String, Object> findBestCatalogMatch(String targetCleanTitle, List<Map<String, Object>> catalogRows) {
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
            String sql = "SELECT song_id, title, duration FROM youtube_catalog WHERE LOWER(artist) = LOWER(?) AND LOWER(album) = LOWER(?) ORDER BY title";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, artistName, albumTitle);
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

        String sql = "SELECT song_id, title, duration FROM youtube_catalog WHERE LOWER(artist) = LOWER(?) AND LOWER(album) = LOWER(?) ORDER BY title";
        List<Map<String, Object>> catalogRows = jdbcTemplate.queryForList(sql, artistName, albumTitle);
        if (catalogRows.isEmpty()) {
            String cleanAlbum = cleanAlbumTitle(albumTitle);
            if (!cleanAlbum.equalsIgnoreCase(albumTitle)) {
                catalogRows = jdbcTemplate.queryForList(sql, artistName, cleanAlbum);
            }
        }
        if (catalogRows.isEmpty()) {
            return existingTracks;
        }

        boolean hasSlashMedley = existingTracks.stream().anyMatch(t -> t.getTitle() != null && t.getTitle().contains(" / "));
        boolean hasMissingTracks = catalogRows.size() > existingTracks.size();
        boolean hasMissingVideoIds = existingTracks.stream().anyMatch(t -> t.getYoutubeVideoId() == null || t.getYoutubeVideoId().isBlank());

        if (!hasSlashMedley && !hasMissingTracks && !hasMissingVideoIds) {
            return existingTracks;
        }

        log.info("Reconciling tracklist for album [{}] by [{}] (DB tracks: {}, Catalog tracks: {})",
                albumTitle, artistName, existingTracks.size(), catalogRows.size());

        if (hasSlashMedley) {
            List<Track> updatedList = new ArrayList<>();
            Set<String> includedSongIds = new HashSet<>();
            int position = 1;

            for (Track t : existingTracks) {
                String origTitle = t.getTitle() != null ? t.getTitle() : "";
                if (origTitle.contains(" / ")) {
                    String[] parts = origTitle.split("\\s*/\\s*");
                    boolean anySubMatch = false;
                    for (String part : parts) {
                        String cleanPart = cleanTrackTitle(part);
                        Map<String, Object> matchedCat = findBestCatalogMatch(cleanPart, catalogRows);
                        if (matchedCat != null) {
                            String songId = (String) matchedCat.get("song_id");
                            if (includedSongIds.add(songId)) {
                                String catTitle = cleanTrackTitle((String) matchedCat.get("title"));
                                Integer dur = matchedCat.get("duration") != null ? ((Number) matchedCat.get("duration")).intValue() : null;
                                Track newTrk = new Track("yt-trk:" + songId, catTitle, position++, dur != null ? dur * 1000 : null, album);
                                newTrk.setYoutubeVideoId(songId);
                                updatedList.add(newTrk);
                                anySubMatch = true;
                            }
                        }
                    }
                    if (!anySubMatch) {
                        t.setPosition(position++);
                        updatedList.add(t);
                    }
                } else {
                    String clean = cleanTrackTitle(origTitle);
                    Map<String, Object> matchedCat = findBestCatalogMatch(clean, catalogRows);
                    if (matchedCat != null) {
                        String songId = (String) matchedCat.get("song_id");
                        includedSongIds.add(songId);
                        if (t.getYoutubeVideoId() == null || t.getYoutubeVideoId().isBlank()) {
                            t.setYoutubeVideoId(songId);
                        }
                    }
                    t.setPosition(position++);
                    updatedList.add(t);
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
                    updatedList.add(extraTrk);
                    includedSongIds.add(songId);
                }
            }

            trackRepository.deleteAll(existingTracks);
            trackRepository.flush();
            return trackRepository.saveAll(updatedList);
        } else {
            Set<String> matchedSongIds = new HashSet<>();
            for (Track t : existingTracks) {
                String clean = cleanTrackTitle(t.getTitle());
                Map<String, Object> matchedCat = findBestCatalogMatch(clean, catalogRows);
                if (matchedCat != null) {
                    String songId = (String) matchedCat.get("song_id");
                    matchedSongIds.add(songId);
                    if (t.getYoutubeVideoId() == null || t.getYoutubeVideoId().isBlank()) {
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
                    boolean alreadyPresent = existingTracks.stream()
                            .anyMatch(et -> cleanTrackTitle(et.getTitle()).equalsIgnoreCase(cleanTitle));
                    if (!alreadyPresent) {
                        Integer dur = row.get("duration") != null ? ((Number) row.get("duration")).intValue() : null;
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
        try {
            mbResponse = musicBrainzClient.getArtistReleases(artistId);
        } catch (Exception ex) {
            log.warn("MusicBrainz artist release lookup failed for [{}]: {}", artistId, ex.getMessage());
        }

        List<MusicBrainzRelease> combined = new ArrayList<>();
        Set<String> seenNormTitles = new HashSet<>();

        if (mbResponse != null && mbResponse.releases() != null) {
            for (MusicBrainzRelease rel : mbResponse.releases()) {
                combined.add(rel);
                if (rel.title() != null) {
                    seenNormTitles.add(normalizeTitle(rel.title()));
                }
            }
        }

        if (jdbcTemplate != null) {
            String artistName = null;
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

            if (artistName != null && !artistName.isBlank()) {
                String sql = "SELECT album, count(*) as track_count FROM youtube_catalog WHERE LOWER(artist) = LOWER(?) GROUP BY album";
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, artistName);
                for (Map<String, Object> row : rows) {
                    String album = (String) row.get("album");
                    if (album == null || album.isBlank()) continue;
                    String norm = normalizeTitle(album);
                    if (seenNormTitles.add(norm)) {
                        int trackCount = row.get("track_count") != null ? ((Number) row.get("track_count")).intValue() : 1;
                        String syntheticId = "yt-" + Base64.getUrlEncoder().withoutPadding().encodeToString((artistName + "::" + album).getBytes(StandardCharsets.UTF_8));
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
                                List.of(new MusicBrainzArtistCredit(artistName, new MusicBrainzArtistReference(artistId, artistName))),
                                rg
                        );
                        combined.add(rel);
                    }
                }
            }
        }

        return new MusicBrainzReleaseSearchResponse(combined.size(), combined);
    }

    public MusicBrainzReleaseSearchResponse searchReleasesWithDataset(String query) {
        MusicBrainzReleaseSearchResponse mbResponse = null;
        try {
            mbResponse = musicBrainzClient.searchReleases(query);
        } catch (Exception ex) {
            log.warn("MusicBrainz release search failed for [{}]: {}", query, ex.getMessage());
        }

        List<MusicBrainzRelease> combined = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        if (mbResponse != null && mbResponse.releases() != null) {
            for (MusicBrainzRelease rel : mbResponse.releases()) {
                combined.add(rel);
                String aName = rel.artistCredit() != null && !rel.artistCredit().isEmpty()
                        ? rel.artistCredit().get(0).name() : "";
                seenKeys.add(normalizeTitle(aName) + "::" + normalizeTitle(rel.title()));
            }
        }

        if (jdbcTemplate != null && query != null && !query.isBlank()) {
            String sql = """
                SELECT artist, album, count(*) as track_count
                FROM youtube_catalog
                WHERE LOWER(album) = LOWER(?)
                   OR LOWER(album) LIKE LOWER(?)
                   OR LOWER(artist) = LOWER(?)
                GROUP BY artist, album
                LIMIT 15
            """;
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, query.trim(), query.trim() + "%", query.trim());
                for (Map<String, Object> row : rows) {
                    String artist = (String) row.get("artist");
                    String album = (String) row.get("album");
                    if (artist == null || album == null || album.isBlank()) continue;

                    String key = normalizeTitle(artist) + "::" + normalizeTitle(album);
                    if (seenKeys.add(key)) {
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
            } catch (Exception ex) {
                log.warn("youtube_catalog album search failed for [{}]: {}", query, ex.getMessage());
            }
        }

        return new MusicBrainzReleaseSearchResponse(combined.size(), combined);
    }

}

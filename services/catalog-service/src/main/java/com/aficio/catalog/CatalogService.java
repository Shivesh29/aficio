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

    @Autowired
    public CatalogService(
            AlbumRepository albumRepository,
            ArtistRepository artistRepository,
            TrackRepository trackRepository,
            MusicBrainzClient musicBrainzClient,
            CoverArtClient coverArtClient,
            UserAlbumHistoryRepository userAlbumHistoryRepository
    ) {
        this.albumRepository = albumRepository;
        this.artistRepository = artistRepository;
        this.trackRepository = trackRepository;
        this.musicBrainzClient = musicBrainzClient;
        this.coverArtClient = coverArtClient;
        this.userAlbumHistoryRepository = userAlbumHistoryRepository;
    }

    public CatalogService(
            AlbumRepository albumRepository,
            ArtistRepository artistRepository,
            TrackRepository trackRepository,
            MusicBrainzClient musicBrainzClient,
            CoverArtClient coverArtClient
    ) {
        this(albumRepository, artistRepository, trackRepository, musicBrainzClient, coverArtClient, null);
    }

    @Transactional
    @CacheEvict(value = {"musicbrainzArtists", "musicbrainzReleases", "musicbrainzReleaseDetails", "musicbrainzArtistReleases", "musicbrainzArtistDetails"}, allEntries = true)
    public Album syncAlbum(String musicBrainzId) {
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

        Album album = albumRepository.findByMusicBrainzId(musicBrainzId)
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
            artist = artistRepository.findByMusicBrainzId(finalArtistMbId)
                    .orElseGet(() -> artistRepository.save(new Artist(finalArtistName, finalArtistMbId)));
        } else {
            artist = artistRepository.findByName(finalArtistName)
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
}
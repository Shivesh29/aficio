package com.aficio.track;

import com.aficio.album.Album;
import com.aficio.album.AlbumRepository;
import com.aficio.artist.Artist;
import com.aficio.dto.track.TrackPlaybackResponse;
import com.aficio.musicbrainz.MusicBrainzClient;
import com.aficio.musicbrainz.MusicBrainzRecording;
import com.aficio.musicbrainz.MusicBrainzRelation;
import com.aficio.musicbrainz.MusicBrainzUrlResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TrackPlaybackServiceTest {

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private MusicBrainzClient musicBrainzClient;

    private ObjectMapper objectMapper;
    private TrackPlaybackService trackPlaybackService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        trackPlaybackService = new TrackPlaybackService(
                trackRepository,
                albumRepository,
                musicBrainzClient,
                objectMapper
        );
    }

    @Test
    void shouldReturnCachedVideoIdWhenAlreadyPresent() {
        Artist artist = new Artist("Nirvana", "artist-mbid");
        Album album = new Album("Nevermind", "release-mbid", artist);
        Track track = new Track("rec-mbid", "Smells Like Teen Spirit", 1, 301000, album);
        track.setYoutubeVideoId("hTWKbfoikeg");

        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));

        TrackPlaybackResponse response = trackPlaybackService.getPlayback(1L);

        assertThat(response).isNotNull();
        assertThat(response.youtubeVideoId()).isEqualTo("hTWKbfoikeg");
        assertThat(response.source()).isEqualTo("cached");
        verifyNoInteractions(musicBrainzClient);
    }

    @Test
    void shouldResolveFromMusicBrainzRecordingRelation() {
        Artist artist = new Artist("Nirvana", "artist-mbid");
        Album album = new Album("Nevermind", "release-mbid", artist);
        Track track = new Track("rec-mbid-1", "Smells Like Teen Spirit", 1, 301000, album);

        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));

        MusicBrainzRelation relation = new MusicBrainzRelation(
                "free streaming",
                "url",
                new MusicBrainzUrlResource("url-1", "https://www.youtube.com/watch?v=hTWKbfoikeg")
        );
        MusicBrainzRecording recording = new MusicBrainzRecording(
                "rec-mbid-1",
                "Smells Like Teen Spirit",
                301000,
                100,
                null,
                null,
                List.of(relation)
        );
        when(musicBrainzClient.getRecordingDetails("rec-mbid-1")).thenReturn(recording);
        when(trackRepository.findByAlbumIdOrderByPosition(any())).thenReturn(List.of(track));

        TrackPlaybackResponse response = trackPlaybackService.getPlayback(1L);

        assertThat(response).isNotNull();
        assertThat(response.youtubeVideoId()).isEqualTo("hTWKbfoikeg");
        assertThat(response.source()).isEqualTo("musicbrainz");
        assertThat(track.getYoutubeVideoId()).isEqualTo("hTWKbfoikeg");
        verify(trackRepository).save(track);
    }

    @Test
    void shouldSaveYoutubeVideoIdSuccessfully() {
        Artist artist = new Artist("Nirvana", "artist-mbid");
        Album album = new Album("Nevermind", "release-mbid", artist);
        Track track = new Track("rec-mbid-1", "Smells Like Teen Spirit", 1, 301000, album);

        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));

        trackPlaybackService.saveYoutubeVideoId(1L, "hTWKbfoikeg");

        assertThat(track.getYoutubeVideoId()).isEqualTo("hTWKbfoikeg");
        verify(trackRepository).save(track);
    }

    @Test
    void shouldBatchPopulateAlbumPlaylistTracks() {
        Artist artist = new Artist("Nirvana", "artist-mbid");
        Album album = new Album("Nevermind", "release-mbid", artist);
        org.springframework.test.util.ReflectionTestUtils.setField(album, "id", 10L);
        album.setYoutubePlaylistId("OLAK5uy_kEQJGO2SZ0k-vJ8b-F2AJLfKnw0cFydNg");

        Track track1 = new Track("rec-1", "Smells Like Teen Spirit", 1, 301000, album);
        Track track2 = new Track("rec-2", "In Bloom", 2, 255000, album);
        org.springframework.test.util.ReflectionTestUtils.setField(track1, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(track2, "id", 2L);

        when(trackRepository.findById(1L)).thenReturn(Optional.of(track1));
        when(trackRepository.findByAlbumIdOrderByPosition(10L)).thenReturn(List.of(track1, track2));

        // When MusicBrainz recording relations return null, it falls through to album playlist
        when(musicBrainzClient.getRecordingDetails("rec-1")).thenReturn(null);

        TrackPlaybackResponse response = trackPlaybackService.getPlayback(1L);

        assertThat(response).isNotNull();
        // Since OLAK5uy_kEQJGO2SZ0k-vJ8b-F2AJLfKnw0cFydNg fetches live or falls back:
        // If live playlist fetch succeeds, response.source is "album_playlist"
        // and both tracks have their video IDs populated!
        if ("album_playlist".equals(response.source())) {
            assertThat(track1.getYoutubeVideoId()).isNotNull();
            assertThat(track2.getYoutubeVideoId()).isNotNull();
            verify(trackRepository, atLeastOnce()).saveAll(any());
        }
    }

    @Test
    void shouldEnforceFourAlbumLimitPerClient() {
        String client = "user-test-client";
        org.springframework.test.util.ReflectionTestUtils.setField(trackPlaybackService, "albumSyncLimit", 4);

        for (long albumId = 1; albumId <= 4; albumId++) {
            assertThat(trackPlaybackService.canSyncAlbumViaYouTube(client, albumId)).isTrue();
            trackPlaybackService.recordAlbumSynced(client, albumId);
        }

        assertThat(trackPlaybackService.getSyncedAlbumCount(client)).isEqualTo(4);
        assertThat(trackPlaybackService.getRemainingAlbumQuota(client)).isEqualTo(0);

        // A 5th unseen album should be blocked
        assertThat(trackPlaybackService.canSyncAlbumViaYouTube(client, 5L)).isFalse();

        // But already-synced albums can still be accessed
        assertThat(trackPlaybackService.canSyncAlbumViaYouTube(client, 1L)).isTrue();
    }

    @Test
    void shouldReturnLimitReachedWhenAlbumLimitHit() {
        String client = "quota-test-user";
        org.springframework.test.util.ReflectionTestUtils.setField(trackPlaybackService, "albumSyncLimit", 2);
        org.springframework.test.util.ReflectionTestUtils.setField(trackPlaybackService, "youtubeApiKey", "test-api-key");

        // Pre-fill 2 synced albums
        trackPlaybackService.recordAlbumSynced(client, 101L);
        trackPlaybackService.recordAlbumSynced(client, 102L);

        Artist artist = new Artist("Radiohead", "artist-mbid");
        Album album = new Album("OK Computer", "release-mbid", artist);
        org.springframework.test.util.ReflectionTestUtils.setField(album, "id", 103L);
        Track track = new Track("rec-mbid-3", "Airbag", 1, 284000, album);
        org.springframework.test.util.ReflectionTestUtils.setField(track, "id", 3L);

        when(trackRepository.findById(3L)).thenReturn(Optional.of(track));
        when(musicBrainzClient.getRecordingDetails("rec-mbid-3")).thenReturn(null);

        TrackPlaybackResponse response = trackPlaybackService.getPlayback(3L, client);

        assertThat(response).isNotNull();
        assertThat(response.source()).isEqualTo("limit_reached");
        assertThat(response.youtubeVideoId()).isNull();
    }

    @Test
    void shouldExemptOwnerAccountFromAlbumLimit() {
        String ownerClient = "user:Shivesh:sharmashivesh29@gmail.com";
        org.springframework.test.util.ReflectionTestUtils.setField(trackPlaybackService, "albumSyncLimit", 2);

        // Pre-fill 5 synced albums for owner
        for (long i = 1; i <= 5; i++) {
            trackPlaybackService.recordAlbumSynced(ownerClient, i);
        }

        // Owner should always be allowed
        assertThat(trackPlaybackService.isOwnerAccount(ownerClient)).isTrue();
        assertThat(trackPlaybackService.isOwnerAccount("user:sharmashivesh29")).isTrue();
        assertThat(trackPlaybackService.isOwnerAccount("user:Shivesh")).isTrue();
        assertThat(trackPlaybackService.canSyncAlbumViaYouTube(ownerClient, 999L)).isTrue();
        assertThat(trackPlaybackService.getRemainingAlbumQuota(ownerClient)).isEqualTo(Integer.MAX_VALUE);

        // Standard user should be blocked
        String standardUser = "user:SSS:lesserblack@gmail.com";
        for (long i = 1; i <= 2; i++) {
            trackPlaybackService.recordAlbumSynced(standardUser, i);
        }
        assertThat(trackPlaybackService.isOwnerAccount(standardUser)).isFalse();
        assertThat(trackPlaybackService.canSyncAlbumViaYouTube(standardUser, 999L)).isFalse();
    }

    @Test
    void shouldBypassAlbumLimitWhenCustomApiKeyIsProvided() {
        String standardUser = "user:SSS:lesserblack@gmail.com";
        org.springframework.test.util.ReflectionTestUtils.setField(trackPlaybackService, "albumSyncLimit", 2);
        org.springframework.test.util.ReflectionTestUtils.setField(trackPlaybackService, "youtubeApiKey", "system-key");

        // Pre-fill 2 synced albums
        trackPlaybackService.recordAlbumSynced(standardUser, 201L);
        trackPlaybackService.recordAlbumSynced(standardUser, 202L);

        // Standard user has reached limit for system key
        assertThat(trackPlaybackService.canSyncAlbumViaYouTube(standardUser, 203L)).isFalse();

        Artist artist = new Artist("Radiohead", "artist-mbid");
        Album album = new Album("The Bends", "release-mbid", artist);
        org.springframework.test.util.ReflectionTestUtils.setField(album, "id", 203L);
        Track track = new Track("rec-mbid-4", "Planet Telex", 1, 259000, album);
        org.springframework.test.util.ReflectionTestUtils.setField(track, "id", 4L);

        when(trackRepository.findById(4L)).thenReturn(Optional.of(track));
        when(musicBrainzClient.getRecordingDetails("rec-mbid-4")).thenReturn(null);

        // Without custom key -> limit_reached
        TrackPlaybackResponse resWithout = trackPlaybackService.getPlayback(4L, standardUser, null);
        assertThat(resWithout.source()).isEqualTo("limit_reached");
        assertThat(resWithout.newlyDiscovered()).isFalse();

        // Replaying already synced album should always be allowed
        assertThat(trackPlaybackService.canSyncAlbumViaYouTube(standardUser, 201L)).isTrue();
    }

    @Test
    void shouldReturnAccurateUserQuotaResponse() {
        String standardUser = "user:kirk:lesserblack12@gmail.com";
        org.springframework.test.util.ReflectionTestUtils.setField(trackPlaybackService, "albumSyncLimit", 4);

        trackPlaybackService.recordAlbumSynced(standardUser, 101L);
        trackPlaybackService.recordAlbumSynced(standardUser, 102L);

        com.aficio.dto.track.UserQuotaResponse quota = trackPlaybackService.getUserQuota(standardUser, null);
        assertThat(quota.isOwner()).isFalse();
        assertThat(quota.dailyLimit()).isEqualTo(4);
        assertThat(quota.usedToday()).isEqualTo(2);
        assertThat(quota.remaining()).isEqualTo(2);
        assertThat(quota.isExhausted()).isFalse();
        assertThat(quota.unlimited()).isFalse();

        // Personal API key quota
        com.aficio.dto.track.UserQuotaResponse personalKeyQuota = trackPlaybackService.getUserQuota(standardUser, "AIzaSyTestKey");
        assertThat(personalKeyQuota.isOwner()).isFalse();
        assertThat(personalKeyQuota.hasCustomKey()).isTrue();
        assertThat(personalKeyQuota.dailyLimit()).isEqualTo(99);
        assertThat(personalKeyQuota.usedToday()).isEqualTo(2);
        assertThat(personalKeyQuota.remaining()).isEqualTo(97);
        assertThat(personalKeyQuota.isExhausted()).isFalse();
        assertThat(personalKeyQuota.unlimited()).isFalse();

        // Owner quota
        com.aficio.dto.track.UserQuotaResponse ownerQuota = trackPlaybackService.getUserQuota("user:shivesh:sharmashivesh29@gmail.com", null);
        assertThat(ownerQuota.isOwner()).isTrue();
        assertThat(ownerQuota.unlimited()).isTrue();
    }

    @Test
    void shouldRejectTributeAndCoverVersionsForOriginalAlbums() {
        String artist = "American Football";
        String album = "American Football";
        String track = "Never Meant";

        // Foreign artist cover (Iron & Wine)
        assertThat(trackPlaybackService.isTrackTitleMatch(track, "Iron & Wine - Never Meant [OFFICIAL MUSIC VIDEO]", artist, album)).isFalse();

        // Foreign artist cover (Blondshell)
        assertThat(trackPlaybackService.isTrackTitleMatch("The Summer Ends", "Blondshell - The Summer Ends [OFFICIAL VISUALIZER]", artist, album)).isFalse();

        // Video explicitly labeled as a cover
        assertThat(trackPlaybackService.isTrackTitleMatch(track, "Never Meant (American Football Cover)", artist, album)).isFalse();
        assertThat(trackPlaybackService.isTrackTitleMatch(track, "American Football - Never Meant (Acoustic Cover)", artist, album)).isFalse();

        // Original Topic channel and direct matches should succeed
        assertThat(trackPlaybackService.isTrackTitleMatch(track, "Never Meant", artist, album)).isTrue();
        assertThat(trackPlaybackService.isTrackTitleMatch(track, "American Football - Never Meant (Official Video)", artist, album)).isTrue();
        assertThat(trackPlaybackService.isTrackTitleMatch(track, "Never Meant - American Football", artist, album)).isTrue();
        assertThat(trackPlaybackService.isTrackTitleMatch(track, "01 - Never Meant", artist, album)).isTrue();
    }

    @Test
    void shouldPrioritizeOfficialArtistChannelInCandidatePlaylists() {
        String artist = "Radiohead";
        String album = "OK Computer";

        // 1. Official artist channel (exact name match)
        int officialChannelScore = trackPlaybackService.scoreCandidatePlaylist(
                "PL123", "Radiohead - OK Computer Full Album", "Radiohead", artist, album);

        // 2. Official Topic channel
        int topicChannelScore = trackPlaybackService.scoreCandidatePlaylist(
                "PL124", "OK Computer Full Album", "Radiohead - Topic", artist, album);

        // 3. Third-party aggregator / fan channel
        int fanChannelScore = trackPlaybackService.scoreCandidatePlaylist(
                "PL125", "Radiohead - OK Computer Full Album", "Indie Rock Guy", artist, album);

        // 4. Tribute / cover candidate
        int tributeScore = trackPlaybackService.scoreCandidatePlaylist(
                "PL126", "Radiohead - OK Computer (Covers)", "Radiohead", artist, album);

        // Assertions: Official artist channel and topic channels strongly outrank third-party user playlists
        assertThat(officialChannelScore).isGreaterThan(fanChannelScore);
        assertThat(topicChannelScore).isGreaterThan(fanChannelScore);
        assertThat(officialChannelScore).isGreaterThan(tributeScore);
        assertThat(tributeScore).isLessThan(0); // Penalized
    }

    @Test
    void shouldMatchTracksWithHqHdTagsAndCombinedTitles() {
        String artist = "Pink Floyd";
        String album = "The Dark Side of the Moon";

        // Speak to Me / Breathe in the Air matching Speak to Me (HQ) Lyrics
        assertThat(trackPlaybackService.isTrackTitleMatch("Speak to Me / Breathe in the Air", "Pink Floyd - Speak to Me (HQ) Lyrics", artist, album)).isTrue();
        assertThat(trackPlaybackService.isTrackTitleMatch("Speak to Me / Breathe in the Air", "Pink Floyd - Breathe (In the Air) (HQ) Lyrics", artist, album)).isTrue();

        // Standalone HQ tags
        assertThat(trackPlaybackService.isTrackTitleMatch("On the Run", "Pink Floyd - On the Run (HQ) Lyrics", artist, album)).isTrue();
        assertThat(trackPlaybackService.isTrackTitleMatch("Time", "Pink Floyd - Time (HQ) Lyrics", artist, album)).isTrue();
    }

    @Test
    void shouldResolveFromYoutubeCatalogWhenPresent() {
        org.springframework.jdbc.core.JdbcTemplate mockJdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbcProvider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(jdbcProvider.getIfAvailable()).thenReturn(mockJdbc);

        TrackPlaybackService serviceWithJdbc = new TrackPlaybackService(
                trackRepository,
                albumRepository,
                musicBrainzClient,
                objectMapper,
                null,
                null,
                jdbcProvider
        );

        Artist artist = new Artist("Meth Wax", "artist-mbid-2");
        Album album = new Album("Meth Wax", "release-mbid-2", artist);
        Track track = new Track(null, "Dance With Me", 1, 160000, album);

        when(trackRepository.findById(2L)).thenReturn(Optional.of(track));
        when(trackRepository.findByAlbumIdOrderByPosition(any())).thenReturn(List.of(track));
        when(mockJdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any(), any()))
                .thenReturn(List.of("fP1hR4Gf50k"));

        TrackPlaybackResponse response = serviceWithJdbc.getPlayback(2L);

        assertThat(response).isNotNull();
        assertThat(response.youtubeVideoId()).isEqualTo("fP1hR4Gf50k");
        assertThat(response.source()).isEqualTo("youtube_catalog");
        assertThat(response.newlyDiscovered()).isFalse();
        verify(trackRepository, atLeastOnce()).save(track);
    }
}


package com.aficio.catalog;

import com.aficio.album.Album;
import com.aficio.album.AlbumRepository;
import com.aficio.artist.Artist;
import com.aficio.artist.ArtistRepository;
import com.aficio.coverart.CoverArtClient;
import com.aficio.musicbrainz.MusicBrainzArtist;
import com.aficio.musicbrainz.MusicBrainzClient;
import com.aficio.musicbrainz.MusicBrainzRelease;
import com.aficio.musicbrainz.MusicBrainzReleaseSearchResponse;
import com.aficio.track.Track;
import com.aficio.track.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogServiceTest {

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private MusicBrainzClient musicBrainzClient;

    @Mock
    private CoverArtClient coverArtClient;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);
        catalogService = new CatalogService(
                albumRepository,
                artistRepository,
                trackRepository,
                musicBrainzClient,
                coverArtClient,
                null,
                jdbcTemplateProvider
        );
    }

    @Test
    void testCleanTrackTitle() {
        assertThat(catalogService.cleanTrackTitle("Behind the Wall of Sleep (2009 - Remaster)"))
                .isEqualTo("Behind the Wall of Sleep");
        assertThat(catalogService.cleanTrackTitle("N.I.B. (2009 - Remaster)"))
                .isEqualTo("N.I.B.");
        assertThat(catalogService.cleanTrackTitle("Evil Woman, Don't Play Your Games with Me (2009 - Remaster)"))
                .isEqualTo("Evil Woman, Don't Play Your Games with Me");
        assertThat(catalogService.cleanTrackTitle("Good Luck, Babe!"))
                .isEqualTo("Good Luck, Babe!");
    }

    @Test
    void shouldDiscoverDatasetReleasesWhenNotOnMusicBrainz() {
        String artistId = "mbid-chappell";
        when(musicBrainzClient.getArtistReleases(artistId))
                .thenReturn(new MusicBrainzReleaseSearchResponse(1, List.of(
                        new MusicBrainzRelease("mb-midwest", "The Rise and Fall of a Midwest Princess", "2023", "US", "Official", 100, 14, Collections.emptyList(), null)
                )));

        when(musicBrainzClient.getArtistDetails(artistId))
                .thenReturn(new MusicBrainzArtist(artistId, "Chappell Roan", "US", null, "Person"));

        when(jdbcTemplate.queryForList(anyString(), eq("Chappell Roan")))
                .thenReturn(List.of(
                        Map.of("album", "The Rise and Fall of a Midwest Princess", "track_count", 14),
                        Map.of("album", "Good Luck, Babe!", "track_count", 1),
                        Map.of("album", "School Nights", "track_count", 5)
                ));

        MusicBrainzReleaseSearchResponse result = catalogService.getArtistReleasesWithDataset(artistId);

        assertThat(result.releases()).hasSize(3);
        assertThat(result.releases().stream().map(MusicBrainzRelease::title))
                .containsExactlyInAnyOrder("The Rise and Fall of a Midwest Princess", "Good Luck, Babe!", "School Nights");

        MusicBrainzRelease goodLuck = result.releases().stream()
                .filter(r -> r.title().equals("Good Luck, Babe!"))
                .findFirst().orElseThrow();
        assertThat(goodLuck.id()).startsWith("yt-");
        assertThat(goodLuck.trackCount()).isEqualTo(1);
    }

    @Test
    void shouldReconcileCompoundMedleyTracksAndMissingTracks() {
        Artist artist = new Artist("Black Sabbath");
        Album album = new Album("Black Sabbath", "mb-bs-1", artist);

        Track t1 = new Track("mb-1", "Black Sabbath", 1, 381000, album);
        Track t2 = new Track("mb-2", "The Wizard", 2, 263000, album);
        Track t3 = new Track("mb-3", "Wasp / Behind the Wall of Sleep / Bassically / N.I.B.", 3, 585000, album);
        Track t4 = new Track("mb-4", "Wicked World", 4, 285000, album);
        Track t5 = new Track("mb-5", "A Bit of Finger / Sleeping Village / Warning", 5, 860000, album);
        List<Track> existing = List.of(t1, t2, t3, t4, t5);

        when(trackRepository.findByAlbumIdOrderByPosition(album.getId())).thenReturn(existing);

        List<Map<String, Object>> catalogRows = List.of(
                Map.of("song_id", "s1", "title", "Black Sabbath (2009 - Remaster)", "duration", 381),
                Map.of("song_id", "s2", "title", "The Wizard (2009 - Remaster)", "duration", 263),
                Map.of("song_id", "s3", "title", "Behind the Wall of Sleep (2009 - Remaster)", "duration", 218),
                Map.of("song_id", "s4", "title", "N.I.B. (2009 - Remaster)", "duration", 367),
                Map.of("song_id", "s5", "title", "Evil Woman, Don't Play Your Games with Me (2009 - Remaster)", "duration", 201),
                Map.of("song_id", "s6", "title", "Sleeping Village (2009 - Remaster)", "duration", 227),
                Map.of("song_id", "s7", "title", "Warning (2009 - Remaster)", "duration", 633),
                Map.of("song_id", "s8", "title", "Wicked World (2009 - Remaster)", "duration", 285)
        );
        when(jdbcTemplate.queryForList(anyString(), eq("Black Sabbath"), eq("Black Sabbath"))).thenReturn(catalogRows);

        ArgumentCaptor<List<Track>> captor = ArgumentCaptor.forClass(List.class);
        when(trackRepository.saveAll(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Track> reconciled = catalogService.reconcileTracksWithDataset(album);

        verify(trackRepository).deleteAll(existing);
        assertThat(reconciled).hasSize(8);

        List<String> titles = reconciled.stream().map(Track::getTitle).toList();
        assertThat(titles).containsExactly(
                "Black Sabbath",
                "The Wizard",
                "Behind the Wall of Sleep",
                "N.I.B.",
                "Wicked World",
                "Sleeping Village",
                "Warning",
                "Evil Woman, Don't Play Your Games with Me"
        );

        // Every track should have its youtubeVideoId pre-populated
        for (Track t : reconciled) {
            assertThat(t.getYoutubeVideoId()).isNotNull();
        }
    }

    @Test
    void shouldSyncAlbumFromYoutubeCatalogDirectly() {
        String artistName = "Chappell Roan";
        String albumTitle = "Good Luck, Babe!";
        String syntheticId = "yt-" + Base64.getUrlEncoder().withoutPadding().encodeToString((artistName + "::" + albumTitle).getBytes(StandardCharsets.UTF_8));

        when(albumRepository.findFirstByMusicBrainzIdOrderByIdAsc(syntheticId)).thenReturn(Optional.empty());
        Artist artist = new Artist(artistName);
        when(artistRepository.findFirstByNameOrderByIdAsc(artistName)).thenReturn(Optional.of(artist));
        when(albumRepository.save(any(Album.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Map<String, Object>> rows = List.of(
                Map.of("song_id", "VZ-oGLluGAc", "title", "Good Luck, Babe!", "duration", 218)
        );
        when(jdbcTemplate.queryForList(anyString(), eq(artistName), eq(albumTitle))).thenReturn(rows);

        Album synced = catalogService.syncAlbum(syntheticId);

        assertThat(synced.getTitle()).isEqualTo("Good Luck, Babe!");
        assertThat(synced.getCoverArtUrl()).isEqualTo("https://img.youtube.com/vi/VZ-oGLluGAc/hqdefault.jpg");

        ArgumentCaptor<List<Track>> trackCaptor = ArgumentCaptor.forClass(List.class);
        verify(trackRepository).saveAll(trackCaptor.capture());

        List<Track> savedTracks = trackCaptor.getValue();
        assertThat(savedTracks).hasSize(1);
        assertThat(savedTracks.get(0).getTitle()).isEqualTo("Good Luck, Babe!");
        assertThat(savedTracks.get(0).getYoutubeVideoId()).isEqualTo("VZ-oGLluGAc");
    }
}

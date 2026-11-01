package com.aficio.catalog;

import com.aficio.artist.Artist;
import com.aficio.artist.ArtistRepository;
import com.aficio.album.Album;
import com.aficio.album.AlbumRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CatalogServiceIntegrationTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Test
    void shouldFindArtistByName() {
        Artist artist = new Artist("Radiohead", "mb-123");
        when(artistRepository.findByName("Radiohead")).thenReturn(Optional.of(artist));

        Optional<Artist> found = artistRepository.findByName("Radiohead");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Radiohead");
        assertThat(found.get().getMusicBrainzId()).isEqualTo("mb-123");
    }

    @Test
    void shouldFindAlbumByMusicBrainzId() {
        Artist artist = new Artist("Pink Floyd");
        Album album = new Album("The Dark Side of the Moon", "mb-456", artist);
        when(albumRepository.findByMusicBrainzId("mb-456")).thenReturn(Optional.of(album));

        Optional<Album> found = albumRepository.findByMusicBrainzId("mb-456");

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("The Dark Side of the Moon");
        assertThat(found.get().getArtist().getName()).isEqualTo("Pink Floyd");
    }
}

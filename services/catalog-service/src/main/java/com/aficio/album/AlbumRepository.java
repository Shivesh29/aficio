package com.aficio.album;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    Optional<Album> findFirstByMusicBrainzIdOrderByIdAsc(String musicBrainzId);

    Optional<Album> findByMusicBrainzId(String musicBrainzId);

    List<Album> findByArtistId(Long artistId);
}
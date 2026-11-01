package com.aficio.track;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackRepository extends JpaRepository<Track, Long> {

    Optional<Track> findFirstByMusicBrainzIdOrderByIdAsc(String musicBrainzId);

    Optional<Track> findByMusicBrainzId(String musicBrainzId);

    List<Track> findByAlbumIdOrderByPosition(Long albumId);

    long countByAlbumId(Long albumId);
}
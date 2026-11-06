package com.aficio.artist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findFirstByMusicBrainzIdOrderByIdAsc(String musicBrainzId);

    Optional<Artist> findFirstByNameOrderByIdAsc(String name);

    Optional<Artist> findByMusicBrainzId(String musicBrainzId);

    List<Artist> findAllByMusicBrainzId(String musicBrainzId);

    Optional<Artist> findByName(String name);
}
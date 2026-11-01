package com.aficio.artist;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findByMusicBrainzId(String musicBrainzId);

    Optional<Artist> findByName(String name);
}
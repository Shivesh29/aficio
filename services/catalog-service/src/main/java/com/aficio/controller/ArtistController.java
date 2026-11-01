package com.aficio.controller;

import com.aficio.dto.album.ArtistResponse;
import com.aficio.artist.Artist;
import com.aficio.artist.ArtistRepository;
import com.aficio.musicbrainz.MusicBrainzArtist;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class ArtistController {

    private final ArtistRepository artistRepository;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ArtistController.class);

    public ArtistController(ArtistRepository artistRepository) {
        this.artistRepository = artistRepository;
    }

    @GetMapping("/artists")
    public List<ArtistResponse> getArtists() {
        return artistRepository.findAll()
                .stream()
                .map(CatalogMapper::toArtistResponse)
                .collect(Collectors.toList());
    }

    @PostMapping("/artists")
    public ArtistResponse createArtist(@Valid @RequestBody ArtistRequest request) {
        Artist artist = new Artist(request.name(), request.musicBrainzId());
        return CatalogMapper.toArtistResponse(artistRepository.save(artist));
    }

    @PostMapping("/artists/import")
    public ArtistResponse importArtist(@Valid @RequestBody MusicBrainzArtistRequest request) {
        Artist existingArtist = artistRepository
                .findByMusicBrainzId(request.musicBrainzId())
                .orElse(null);

        if (existingArtist != null) {
            return CatalogMapper.toArtistResponse(existingArtist);
        }
        Artist artist = new Artist(request.name(), request.musicBrainzId());
        return CatalogMapper.toArtistResponse(artistRepository.save(artist));
    }

    public record ArtistRequest(
            @NotBlank(message = "Name is required")
            String name,
            String musicBrainzId
    ) {
    }

    public record MusicBrainzArtistRequest(
            String name,
            String musicBrainzId
    ) {
    }
}
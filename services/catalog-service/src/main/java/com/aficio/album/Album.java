package com.aficio.album;

import com.aficio.artist.Artist;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String musicBrainzId;

    private String coverArtUrl;

    private String youtubePlaylistId;

    @ManyToOne
    private Artist artist;

    protected Album() {
    }

    public Album(String title, String musicBrainzId, Artist artist) {
        this.title = title;
        this.musicBrainzId = musicBrainzId;
        this.artist = artist;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMusicBrainzId() {
        return musicBrainzId;
    }

    public void setMusicBrainzId(String musicBrainzId) {
        this.musicBrainzId = musicBrainzId;
    }

    public String getCoverArtUrl() {
        return coverArtUrl;
    }

    public void setCoverArtUrl(String coverArtUrl) {
        this.coverArtUrl = coverArtUrl;
    }

    public String getYoutubePlaylistId() {
        return youtubePlaylistId;
    }

    public void setYoutubePlaylistId(String youtubePlaylistId) {
        this.youtubePlaylistId = youtubePlaylistId;
    }

    public Artist getArtist() {
        return artist;
    }
}
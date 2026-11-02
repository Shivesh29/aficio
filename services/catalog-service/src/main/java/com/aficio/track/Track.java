package com.aficio.track;

import com.aficio.album.Album;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String musicBrainzId;

    private String title;

    private Integer position;

    private Integer lengthMilliseconds;

    private String youtubeVideoId;

    @ManyToOne
    private Album album;

    protected Track() {
    }

    public Track(
            String musicBrainzId,
            String title,
            Integer position,
            Integer lengthMilliseconds,
            Album album
    ) {
        this.musicBrainzId = musicBrainzId;
        this.title = title;
        this.position = position;
        this.lengthMilliseconds = lengthMilliseconds;
        this.album = album;
    }

    public Long getId() {
        return id;
    }

    public String getMusicBrainzId() {
        return musicBrainzId;
    }

    public void setMusicBrainzId(String musicBrainzId) {
        this.musicBrainzId = musicBrainzId;
    }

    public String getTitle() {
        return title;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public Integer getLengthMilliseconds() {
        return lengthMilliseconds;
    }

    public Album getAlbum() {
        return album;
    }

    public String getYoutubeVideoId() {
        return youtubeVideoId;
    }

    public void setYoutubeVideoId(String youtubeVideoId) {
        this.youtubeVideoId = youtubeVideoId;
    }
}
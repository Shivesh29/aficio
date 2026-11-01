package com.aficio.artist;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String musicBrainzId;

    protected Artist() {
    }

    public Artist(String name) {
        this.name = name;
    }

    public Artist(String name, String musicBrainzId) {
        this.name = name;
        this.musicBrainzId = musicBrainzId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMusicBrainzId() {
        return musicBrainzId;
    }

    public void setMusicBrainzId(String musicBrainzId) {
        this.musicBrainzId = musicBrainzId;
    }
}
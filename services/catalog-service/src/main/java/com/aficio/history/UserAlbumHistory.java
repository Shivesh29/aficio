package com.aficio.history;

import com.aficio.album.Album;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_album_history",
        uniqueConstraints = @UniqueConstraint(columnNames = {"username", "album_id"})
)
public class UserAlbumHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @Column(name = "played_at", nullable = false)
    private LocalDateTime playedAt;

    @Column(nullable = false)
    private boolean synced;

    protected UserAlbumHistory() {
    }

    public UserAlbumHistory(String username, Album album, boolean synced) {
        this.username = username != null ? username.trim().toLowerCase(java.util.Locale.ROOT) : null;
        this.album = album;
        this.synced = synced;
        this.playedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username != null ? username.trim().toLowerCase(java.util.Locale.ROOT) : null;
    }

    public Album getAlbum() {
        return album;
    }

    public void setAlbum(Album album) {
        this.album = album;
    }

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(LocalDateTime playedAt) {
        this.playedAt = playedAt;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}

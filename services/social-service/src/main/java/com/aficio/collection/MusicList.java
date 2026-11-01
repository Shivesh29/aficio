package com.aficio.collection;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "music_list")
@Getter
@Setter
@NoArgsConstructor
public class MusicList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private boolean isPublic;
    private LocalDateTime createdAt;

    @Column(name = "owner_id")
    private Long ownerId;

    public MusicList(Long ownerId, String title, String description, boolean isPublic) {
        this.ownerId = ownerId;
        this.title = title;
        this.description = description;
        this.isPublic = isPublic;
        this.createdAt = LocalDateTime.now();
    }
}
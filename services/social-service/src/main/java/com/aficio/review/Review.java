package com.aficio.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "album_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "album_id")
    private Long albumId;

    private Double rating;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createdAt;

    public Review(Long userId, Long albumId, Double rating, String content) {
        this.userId = userId;
        this.albumId = albumId;
        this.rating = rating;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}
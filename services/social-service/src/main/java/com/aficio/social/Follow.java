package com.aficio.social;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "following_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_id")
    private Long followerId;

    @Column(name = "following_id")
    private Long followingId;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACCEPTED";

    private LocalDateTime createdAt;

    public Follow(Long followerId, Long followingId) {
        this(followerId, followingId, "ACCEPTED");
    }

    public Follow(Long followerId, Long followingId, String status) {
        this.followerId = followerId;
        this.followingId = followingId;
        this.status = status != null ? status : "ACCEPTED";
        this.createdAt = LocalDateTime.now();
    }
}
package com.aficio.activity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "activity")
@Getter
@NoArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "actor_id")
    private Long actorId;

    private String actorUsername;

    private String type;

    private String data;

    private LocalDateTime createdAt;

    public Activity(Long userId, Long actorId, String actorUsername, String type, String data) {
        this.userId = userId;
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.type = type;
        this.data = data;
        this.createdAt = LocalDateTime.now();
    }
}
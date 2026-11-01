package com.aficio.message;

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

@Entity
@Table(name = "message_thread", uniqueConstraints = @UniqueConstraint(columnNames = {"first_user_id", "second_user_id"}))
@Getter
@NoArgsConstructor
public class MessageThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_user_id")
    private Long firstUserId;

    @Column(name = "second_user_id")
    private Long secondUserId;

    private LocalDateTime createdAt;

    public MessageThread(Long firstUserId, Long secondUserId) {
        this.firstUserId = firstUserId;
        this.secondUserId = secondUserId;
        this.createdAt = LocalDateTime.now();
    }
}
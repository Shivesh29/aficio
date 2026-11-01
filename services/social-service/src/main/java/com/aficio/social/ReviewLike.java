package com.aficio.social;

import java.time.LocalDateTime;
import com.aficio.review.Review;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "review_like", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "review_id"}))
@Getter
@NoArgsConstructor
public class ReviewLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    private Review review;

    private LocalDateTime createdAt;

    public ReviewLike(Long userId, Review review) {
        this.userId = userId;
        this.review = review;
        this.createdAt = LocalDateTime.now();
    }
}
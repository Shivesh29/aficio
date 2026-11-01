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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "review_comment")
@Getter
@Setter
@NoArgsConstructor
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    private Review review;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createdAt;

    public ReviewComment(Long userId, Review review, String content) {
        this.userId = userId;
        this.review = review;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}
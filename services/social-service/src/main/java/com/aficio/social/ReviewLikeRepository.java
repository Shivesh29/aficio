package com.aficio.social;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {

    Optional<ReviewLike> findByUserIdAndReviewId(Long userId, Long reviewId);

    long countByReviewId(Long reviewId);

    void deleteByReviewId(Long reviewId);
}
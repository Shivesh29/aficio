package com.aficio.social;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    List<ReviewComment> findByReviewIdOrderByCreatedAtAsc(Long reviewId);

    void deleteByReviewId(Long reviewId);
}
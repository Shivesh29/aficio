package com.aficio.review;

import com.aficio.config.AlbumClient;
import com.aficio.config.UserClient;
import com.aficio.dto.user.UserSummaryResponse;
import com.aficio.social.ReviewCommentRepository;
import com.aficio.social.ReviewLikeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final UserClient userClient;
    private final AlbumClient albumClient;

    @Transactional
    @CacheEvict(value = {"albumReviews", "userReviews"}, allEntries = true)
    public Review saveReview(
            String username,
            Long albumId,
            Double rating,
            String content
    ) {
        UserSummaryResponse userInfo = userClient.findByUsername(username);
        if (userInfo == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found");
        }
        // Verify album exists (will throw if not)
        albumClient.findById(albumId);

        Review review = reviewRepository.findByUserIdAndAlbumId(userInfo.id(), albumId)
                .orElse(new Review(userInfo.id(), albumId, rating, content));

        if (rating != null) {
            review.setRating(rating);
        }
        review.setContent(content);

        return reviewRepository.save(review);
    }

    @Transactional
    @CacheEvict(value = {"albumReviews", "userReviews"}, allEntries = true)
    public Review saveRating(
            String username,
            Long albumId,
            Double rating
    ) {
        UserSummaryResponse userInfo = userClient.findByUsername(username);
        if (userInfo == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found");
        }
        albumClient.findById(albumId);

        Review review = reviewRepository.findByUserIdAndAlbumId(userInfo.id(), albumId)
                .orElse(new Review(userInfo.id(), albumId, rating, null));

        review.setRating(rating);
        return reviewRepository.save(review);
    }

    @Transactional
    @CacheEvict(value = {"albumReviews", "userReviews"}, allEntries = true)
    public void deleteRating(String username, Long albumId) {
        UserSummaryResponse userInfo = userClient.findByUsername(username);
        if (userInfo == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found");
        }
        Review review = reviewRepository.findByUserIdAndAlbumId(userInfo.id(), albumId)
                .orElse(null);
        if (review == null) {
            return;
        }

        if (review.getContent() == null || review.getContent().trim().isEmpty()) {
            reviewCommentRepository.deleteByReviewId(review.getId());
            reviewLikeRepository.deleteByReviewId(review.getId());
            reviewRepository.delete(review);
        } else {
            review.setRating(null);
            reviewRepository.save(review);
        }
    }

    @Cacheable(value = "albumReviews", key = "#albumId")
    public List<Review> getAlbumReviews(Long albumId) {
        return reviewRepository.findByAlbumId(albumId);
    }

    @Cacheable(value = "userReviews", key = "#userId")
    public List<Review> getUserReviews(Long userId) {
        return reviewRepository.findByUserId(userId);
    }

    @Transactional
    @CacheEvict(value = {"albumReviews", "userReviews"}, allEntries = true)
    public void deleteReview(String username, Long reviewId) {
        UserSummaryResponse userInfo = userClient.findByUsername(username);
        if (userInfo == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found");
        }
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Review not found"));
        if (!review.getUserId().equals(userInfo.id())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Cannot delete another user's review");
        }
        reviewCommentRepository.deleteByReviewId(reviewId);
        reviewLikeRepository.deleteByReviewId(reviewId);
        reviewRepository.delete(review);
    }
}
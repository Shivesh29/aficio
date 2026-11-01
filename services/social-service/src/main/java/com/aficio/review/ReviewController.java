package com.aficio.review;

import com.aficio.dto.review.ReviewResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final com.aficio.config.UserClient userClient;
    private final com.aficio.config.AlbumClient albumClient;

    private ReviewResponse toResponse(Review review) {
        String username = null;
        try {
            var user = userClient.findById(review.getUserId());
            if (user != null) {
                username = user.username();
            }
        } catch (Exception ignored) {}

        com.aficio.dto.album.AlbumResponse albumResp = null;
        try {
            albumResp = albumClient.findById(review.getAlbumId());
        } catch (Exception ignored) {}

        if (albumResp == null) {
            albumResp = new com.aficio.dto.album.AlbumResponse(
                    review.getAlbumId(),
                    null,
                    null,
                    null,
                    null
            );
        }

        return new ReviewResponse(
                review.getId(),
                new com.aficio.dto.user.UserSummaryResponse(review.getUserId(), username),
                albumResp,
                review.getRating(),
                review.getContent(),
                review.getCreatedAt(),
                0,
                false
        );
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @Valid @RequestBody ReviewRequest request,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        Review review = reviewService.saveReview(
                authentication.getName(),
                request.albumId(),
                request.rating(),
                request.content()
        );
        return ResponseEntity.ok(toResponse(review));
    }

    @PostMapping("/rate")
    public ResponseEntity<ReviewResponse> rateAlbum(
            @Valid @RequestBody RateRequest request,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        Review review = reviewService.saveRating(
                authentication.getName(),
                request.albumId(),
                request.rating()
        );
        return ResponseEntity.ok(toResponse(review));
    }

    @DeleteMapping("/rate/album/{albumId}")
    public ResponseEntity<Void> removeRating(
            @PathVariable Long albumId,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        reviewService.deleteRating(authentication.getName(), albumId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/album/{albumId}")
    public ResponseEntity<List<ReviewResponse>> getAlbumReviews(@PathVariable Long albumId) {
        return ResponseEntity.ok(
                reviewService.getAlbumReviews(albumId).stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList())
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReviewResponse>> getUserReviews(@PathVariable Long userId) {
        return ResponseEntity.ok(
                reviewService.getUserReviews(userId).stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList())
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long id,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        reviewService.deleteReview(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    public record RateRequest(
            @NotNull(message = "Album ID is required")
            Long albumId,
            @NotNull(message = "Rating is required")
            @Min(value = 1, message = "Rating must be at least 1")
            @Max(value = 5, message = "Rating cannot exceed 5")
            Double rating
    ) {}

    public record ReviewRequest(
            @NotNull(message = "Album ID is required")
            Long albumId,
            @Min(value = 1, message = "Rating must be at least 1")
            @Max(value = 5, message = "Rating cannot exceed 5")
            Double rating,
            String content
    ) {}
}
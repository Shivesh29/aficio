package com.aficio.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aficio.config.UserClient;
import com.aficio.dto.social.FollowResponse;
import com.aficio.dto.social.ReviewCommentResponse;
import com.aficio.activity.Activity;
import com.aficio.activity.ActivityRepository;
import com.aficio.notification.NotificationService;
import com.aficio.review.Review;
import com.aficio.review.ReviewRepository;
import com.aficio.social.Follow;
import com.aficio.social.FollowRepository;
import com.aficio.social.ReviewComment;
import com.aficio.social.ReviewCommentRepository;
import com.aficio.social.ReviewLike;
import com.aficio.social.ReviewLikeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SocialController {

    private final UserClient userClient;
    private final FollowRepository followRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final NotificationService notificationService;
    private final ActivityRepository activityRepository;
    private static final Logger log = LoggerFactory.getLogger(SocialController.class);

    public SocialController(
            UserClient userClient,
            FollowRepository followRepository,
            ReviewRepository reviewRepository,
            ReviewLikeRepository reviewLikeRepository,
            ReviewCommentRepository reviewCommentRepository,
            NotificationService notificationService,
            ActivityRepository activityRepository
    ) {
        this.userClient = userClient;
        this.followRepository = followRepository;
        this.reviewRepository = reviewRepository;
        this.reviewLikeRepository = reviewLikeRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.notificationService = notificationService;
        this.activityRepository = activityRepository;
    }

    private void sendSocialEvent(String type, String actorUsername, Long recipientId, String data) {
        try {
            if (recipientId != null) {
                var recipient = userClient.findById(recipientId);
                String recipientUsername = recipient != null ? recipient.username() : null;
                notificationService.createNotification(recipientId, recipientUsername, actorUsername, type, data);
            }
        } catch (Exception e) {
            log.warn("Failed to create social notification for recipientId={}: {}", recipientId, e.getMessage());
        }
    }

    private void sendActivityEvent(String type, Long actorId, String actorUsername, Long userId, String data) {
        try {
            if (userId != null) {
                activityRepository.save(new Activity(userId, actorId, actorUsername, type, data));
            }
        } catch (Exception e) {
            log.warn("Failed to save activity for userId={}: {}", userId, e.getMessage());
        }
    }

    @PostMapping("/users/{username}/follow")
    public FollowResponse follow(
            @PathVariable String username,
            Authentication authentication
    ) {
        com.aficio.dto.user.UserSummaryResponse followerInfo = currentUser(authentication);
        com.aficio.dto.user.UserSummaryResponse followingInfo = userClient.findByUsername(username);
        if (followingInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        if (followerInfo.id().equals(followingInfo.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot follow yourself");
        }

        boolean isPrivate = Boolean.TRUE.equals(followingInfo.isPrivate());
        String initialStatus = isPrivate ? "PENDING" : "ACCEPTED";

        Follow follow = followRepository.findByFollowerIdAndFollowingId(followerInfo.id(), followingInfo.id())
                .orElseGet(() -> {
                    Follow newFollow = new Follow(followerInfo.id(), followingInfo.id(), initialStatus);
                    return followRepository.save(newFollow);
                });

        if (isPrivate && "PENDING".equals(follow.getStatus())) {
            sendSocialEvent("FOLLOW_REQUEST", followerInfo.username(), followingInfo.id(), "sent you a follow request");
        } else if ("ACCEPTED".equals(follow.getStatus())) {
            sendSocialEvent("FOLLOW", followerInfo.username(), followingInfo.id(), null);
            sendActivityEvent("FOLLOW", followerInfo.id(), followerInfo.username(), followingInfo.id(), null);
        }

        return new FollowResponse(
                follow.getId(),
                new com.aficio.dto.user.UserSummaryResponse(followerInfo.id(), followerInfo.username(), followerInfo.isPrivate()),
                new com.aficio.dto.user.UserSummaryResponse(followingInfo.id(), followingInfo.username(), followingInfo.isPrivate()),
                follow.getStatus(),
                follow.getCreatedAt()
        );
    }

    @DeleteMapping("/users/{username}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(
            @PathVariable String username,
            Authentication authentication
    ) {
        com.aficio.dto.user.UserSummaryResponse followerInfo = currentUser(authentication);
        com.aficio.dto.user.UserSummaryResponse followingInfo = userClient.findByUsername(username);
        if (followingInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        followRepository.findByFollowerIdAndFollowingId(followerInfo.id(), followingInfo.id())
                .ifPresent(followRepository::delete);
    }

    @GetMapping("/users/{username}/followers")
    public List<FollowResponse> followers(@PathVariable String username, Authentication authentication) {
        com.aficio.dto.user.UserSummaryResponse targetUser = userClient.findByUsername(username);
        if (targetUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        if (Boolean.TRUE.equals(targetUser.isPrivate())) {
            boolean isAllowed = false;
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
                com.aficio.dto.user.UserSummaryResponse current = userClient.findByUsername(authentication.getName());
                if (current != null && (current.id().equals(targetUser.id()) ||
                        followRepository.findByFollowerIdAndFollowingIdAndStatus(current.id(), targetUser.id(), "ACCEPTED").isPresent())) {
                    isAllowed = true;
                }
            }
            if (!isAllowed) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is private");
            }
        }

        return followRepository.findByFollowingIdAndStatus(targetUser.id(), "ACCEPTED")
                .stream()
                .map(follow -> {
                    com.aficio.dto.user.UserSummaryResponse followerUser;
                    try {
                        followerUser = userClient.findById(follow.getFollowerId());
                    } catch (Exception e) {
                        followerUser = null;
                    }
                    if (followerUser == null) {
                        followerUser = new com.aficio.dto.user.UserSummaryResponse(follow.getFollowerId(), "user_" + follow.getFollowerId(), false);
                    }
                    return new FollowResponse(
                            follow.getId(),
                            followerUser,
                            targetUser,
                            follow.getStatus(),
                            follow.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/users/{username}/following")
    public List<FollowResponse> following(@PathVariable String username, Authentication authentication) {
        com.aficio.dto.user.UserSummaryResponse targetUser = userClient.findByUsername(username);
        if (targetUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        if (Boolean.TRUE.equals(targetUser.isPrivate())) {
            boolean isAllowed = false;
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
                com.aficio.dto.user.UserSummaryResponse current = userClient.findByUsername(authentication.getName());
                if (current != null && (current.id().equals(targetUser.id()) ||
                        followRepository.findByFollowerIdAndFollowingIdAndStatus(current.id(), targetUser.id(), "ACCEPTED").isPresent())) {
                    isAllowed = true;
                }
            }
            if (!isAllowed) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is private");
            }
        }

        return followRepository.findByFollowerIdAndStatus(targetUser.id(), "ACCEPTED")
                .stream()
                .map(follow -> {
                    com.aficio.dto.user.UserSummaryResponse followingUser;
                    try {
                        followingUser = userClient.findById(follow.getFollowingId());
                    } catch (Exception e) {
                        followingUser = null;
                    }
                    if (followingUser == null) {
                        followingUser = new com.aficio.dto.user.UserSummaryResponse(follow.getFollowingId(), "user_" + follow.getFollowingId(), false);
                    }
                    return new FollowResponse(
                            follow.getId(),
                            targetUser,
                            followingUser,
                            follow.getStatus(),
                            follow.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/users/{username}/is-following")
    public boolean isFollowing(@PathVariable String username, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return false;
        }
        com.aficio.dto.user.UserSummaryResponse currentInfo = currentUser(authentication);
        com.aficio.dto.user.UserSummaryResponse targetInfo = userClient.findByUsername(username);
        if (targetInfo == null || currentInfo == null) {
            return false;
        }
        return followRepository.findByFollowerIdAndFollowingIdAndStatus(currentInfo.id(), targetInfo.id(), "ACCEPTED").isPresent();
    }

    @GetMapping("/users/{username}/follow-status")
    public FollowStatusResponse followStatus(@PathVariable String username, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return new FollowStatusResponse("NONE", false, false);
        }
        com.aficio.dto.user.UserSummaryResponse currentInfo = currentUser(authentication);
        com.aficio.dto.user.UserSummaryResponse targetInfo = userClient.findByUsername(username);
        if (targetInfo == null) {
            return new FollowStatusResponse("NONE", false, false);
        }
        var followOpt = followRepository.findByFollowerIdAndFollowingId(currentInfo.id(), targetInfo.id());
        if (followOpt.isEmpty()) {
            return new FollowStatusResponse("NONE", false, false);
        }
        Follow follow = followOpt.get();
        if ("ACCEPTED".equalsIgnoreCase(follow.getStatus())) {
            return new FollowStatusResponse("FOLLOWING", true, false);
        } else if ("PENDING".equalsIgnoreCase(follow.getStatus())) {
            return new FollowStatusResponse("PENDING", false, true);
        } else {
            return new FollowStatusResponse("NONE", false, false);
        }
    }

    @GetMapping("/users/follow-requests/pending")
    public List<FollowResponse> pendingFollowRequests(Authentication authentication) {
        com.aficio.dto.user.UserSummaryResponse current = currentUser(authentication);
        return followRepository.findByFollowingIdAndStatus(current.id(), "PENDING")
                .stream()
                .map(follow -> {
                    com.aficio.dto.user.UserSummaryResponse requester;
                    try {
                        requester = userClient.findById(follow.getFollowerId());
                    } catch (Exception e) {
                        requester = new com.aficio.dto.user.UserSummaryResponse(follow.getFollowerId(), "user_" + follow.getFollowerId(), false);
                    }
                    return new FollowResponse(
                            follow.getId(),
                            requester,
                            current,
                            follow.getStatus(),
                            follow.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/users/follow-requests/{requestId}/accept")
    public FollowResponse acceptFollowRequest(@PathVariable Long requestId, Authentication authentication) {
        com.aficio.dto.user.UserSummaryResponse current = currentUser(authentication);
        Follow follow = followRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Follow request not found"));

        if (!follow.getFollowingId().equals(current.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to accept this request");
        }

        follow.setStatus("ACCEPTED");
        followRepository.save(follow);

        com.aficio.dto.user.UserSummaryResponse follower = userClient.findById(follow.getFollowerId());
        sendSocialEvent("FOLLOW_ACCEPT", current.username(), follow.getFollowerId(), "accepted your follow request");
        sendActivityEvent("FOLLOW", follow.getFollowerId(), follower.username(), current.id(), null);

        return new FollowResponse(
                follow.getId(),
                follower,
                current,
                follow.getStatus(),
                follow.getCreatedAt()
        );
    }

    @PostMapping("/users/follow-requests/{requestId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineFollowRequest(@PathVariable Long requestId, Authentication authentication) {
        com.aficio.dto.user.UserSummaryResponse current = currentUser(authentication);
        Follow follow = followRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Follow request not found"));

        if (!follow.getFollowingId().equals(current.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to decline this request");
        }

        followRepository.delete(follow);
    }

    public record FollowStatusResponse(
            String status,
            boolean following,
            boolean pending
    ) {
    }

    @GetMapping("/reviews/{reviewId}/is-liked")
    public boolean isLiked(@PathVariable Long reviewId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return false;
        }
        com.aficio.dto.user.UserSummaryResponse current = currentUser(authentication);
        return reviewLikeRepository.findByUserIdAndReviewId(current.id(), reviewId).isPresent();
    }

    @PostMapping("/reviews/{reviewId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeReview(
            @PathVariable Long reviewId,
            Authentication authentication
    ) {
        com.aficio.dto.user.UserSummaryResponse userInfo = currentUser(authentication);
        Review review = findReview(reviewId);
        reviewLikeRepository.findByUserIdAndReviewId(userInfo.id(), reviewId)
                .orElseGet(() -> {
                    ReviewLike like = reviewLikeRepository.save(new ReviewLike(userInfo.id(), review));
                    sendSocialEvent("LIKE", userInfo.username(), review.getUserId(), null);
                    sendActivityEvent("LIKE", userInfo.id(), userInfo.username(), review.getUserId(), null);
                    return like;
                });
    }

    @DeleteMapping("/reviews/{reviewId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeReview(
            @PathVariable Long reviewId,
            Authentication authentication
    ) {
        com.aficio.dto.user.UserSummaryResponse userInfo = currentUser(authentication);
        reviewLikeRepository.findByUserIdAndReviewId(userInfo.id(), reviewId)
                .ifPresent(reviewLikeRepository::delete);
    }

    @GetMapping("/reviews/{reviewId}/likes")
    public long likeCount(@PathVariable Long reviewId) {
        findReview(reviewId);
        return reviewLikeRepository.countByReviewId(reviewId);
    }

    @PostMapping("/reviews/{reviewId}/comments")
    public ReviewCommentResponse comment(
            @PathVariable Long reviewId,
            @Valid @RequestBody CommentRequest request,
            Authentication authentication
    ) {
        com.aficio.dto.user.UserSummaryResponse userInfo = currentUser(authentication);
        Review review = findReview(reviewId);
        ReviewComment comment = reviewCommentRepository.save(new ReviewComment(
                userInfo.id(),
                review,
                request.content()
        ));

        sendSocialEvent("COMMENT", userInfo.username(), review.getUserId(), null);
        sendActivityEvent("COMMENT", userInfo.id(), userInfo.username(), review.getUserId(), null);

        return new ReviewCommentResponse(
                comment.getId(),
                new com.aficio.dto.user.UserSummaryResponse(userInfo.id(), userInfo.username()),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }

    @GetMapping("/reviews/{reviewId}/comments")
    public List<ReviewCommentResponse> comments(@PathVariable Long reviewId) {
        findReview(reviewId);
        return reviewCommentRepository.findByReviewIdOrderByCreatedAtAsc(reviewId)
                .stream()
                .map(comment -> {
                    String username = null;
                    try {
                        var user = userClient.findById(comment.getUserId());
                        if (user != null) {
                            username = user.username();
                        }
                    } catch (Exception ignored) {}
                    return new ReviewCommentResponse(
                            comment.getId(),
                            new com.aficio.dto.user.UserSummaryResponse(comment.getUserId(), username),
                            comment.getContent(),
                            comment.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());
    }

    private com.aficio.dto.user.UserSummaryResponse currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userClient.findByUsername(authentication.getName());
    }

    private Review findReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
    }

    public record CommentRequest(
            @NotBlank(message = "Content is required")
            String content
    ) {
    }
}
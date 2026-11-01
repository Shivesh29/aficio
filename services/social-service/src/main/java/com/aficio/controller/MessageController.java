package com.aficio.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aficio.config.UserClient;
import com.aficio.dto.message.ConversationSummaryResponse;
import com.aficio.dto.message.DirectMessageResponse;
import com.aficio.activity.Activity;
import com.aficio.activity.ActivityRepository;
import com.aficio.notification.NotificationService;
import com.aficio.message.DirectMessage;
import com.aficio.message.DirectMessageRepository;
import com.aficio.social.FollowRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final DirectMessageRepository messageRepository;
    private final UserClient userClient;
    private final FollowRepository followRepository;
    private final NotificationService notificationService;
    private final ActivityRepository activityRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    public MessageController(
            DirectMessageRepository messageRepository,
            UserClient userClient,
            FollowRepository followRepository,
            NotificationService notificationService,
            ActivityRepository activityRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.messageRepository = messageRepository;
        this.userClient = userClient;
        this.followRepository = followRepository;
        this.notificationService = notificationService;
        this.activityRepository = activityRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/conversations")
    public List<ConversationSummaryResponse> conversations(Authentication authentication) {
        com.aficio.dto.user.UserSummaryResponse current = currentUser(authentication);
        List<DirectMessage> allMessages = messageRepository
                .findBySenderIdOrRecipientIdOrderBySentAtDesc(current.id(), current.id());

        Map<Long, ConversationSummaryResponse> summaryMap = new LinkedHashMap<>();
        for (DirectMessage msg : allMessages) {
            boolean sentByCurrentUser = msg.getSenderId().equals(current.id());
            Long partnerId = sentByCurrentUser ? msg.getRecipientId() : msg.getSenderId();
            String partnerUsername = current.username();
            if (!partnerId.equals(current.id())) {
                try {
                    com.aficio.dto.user.UserSummaryResponse partnerUser = userClient.findById(partnerId);
                    if (partnerUser != null) {
                        partnerUsername = partnerUser.username();
                    } else {
                        partnerUsername = "user_" + partnerId;
                    }
                } catch (Exception e) {
                    partnerUsername = "user_" + partnerId;
                }
            }
            com.aficio.dto.user.UserSummaryResponse partner = new com.aficio.dto.user.UserSummaryResponse(partnerId, partnerUsername);
            if (!summaryMap.containsKey(partner.id())) {
                long unreadCount = messageRepository.countByRecipientIdAndSenderIdAndIsReadFalse(current.id(), partner.id());
                summaryMap.put(partner.id(), new ConversationSummaryResponse(
                        partner.id(),
                        partner.username(),
                        msg.getContent(),
                        msg.getSentAt(),
                        sentByCurrentUser,
                        unreadCount
                ));
            }
        }
        return new ArrayList<>(summaryMap.values());
    }

    @GetMapping("/{username}/check")
    public DmRecipientStatusResponse checkRecipient(
            @PathVariable String username,
            Authentication authentication
    ) {
        com.aficio.dto.user.UserSummaryResponse current = currentUser(authentication);
        com.aficio.dto.user.UserSummaryResponse target;
        try {
            target = userClient.findByUsername(username);
        } catch (Exception e) {
            target = null;
        }

        if (target == null) {
            return new DmRecipientStatusResponse(
                    false,
                    username,
                    null,
                    false,
                    "NONE",
                    false,
                    "Account @" + username + " does not exist."
            );
        }

        if (target.id().equals(current.id())) {
            return new DmRecipientStatusResponse(
                    true,
                    target.username(),
                    target.id(),
                    false,
                    "SELF",
                    false,
                    "You cannot direct message yourself."
            );
        }

        var followOpt = followRepository.findByFollowerIdAndFollowingId(current.id(), target.id());
        String followStatus = "NONE";
        if (followOpt.isPresent()) {
            followStatus = followOpt.get().getStatus();
        }

        boolean isFollowedBy = followRepository.findByFollowerIdAndFollowingIdAndStatus(target.id(), current.id(), "ACCEPTED").isPresent();

        boolean canMessage = "ACCEPTED".equalsIgnoreCase(followStatus) || isFollowedBy;
        String reason = null;
        if (!canMessage) {
            if ("PENDING".equalsIgnoreCase(followStatus)) {
                reason = "Your follow request to @" + target.username() + " is pending approval.";
            } else {
                reason = "You must follow @" + target.username() + " and have your follow request approved before sending direct messages.";
            }
        }

        return new DmRecipientStatusResponse(
                true,
                target.username(),
                target.id(),
                canMessage,
                followStatus,
                isFollowedBy,
                reason
        );
    }

    @PostMapping("/{username}")
    public DirectMessageResponse send(
            @PathVariable String username,
            @Valid @RequestBody MessageRequest request,
            Authentication authentication
    ) {
        com.aficio.dto.user.UserSummaryResponse senderInfo = currentUser(authentication);
        com.aficio.dto.user.UserSummaryResponse recipientInfo;
        try {
            recipientInfo = userClient.findByUsername(username);
        } catch (Exception e) {
            recipientInfo = null;
        }

        if (recipientInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account @" + username + " does not exist.");
        }

        if (senderInfo.id().equals(recipientInfo.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot send direct messages to yourself");
        }

        boolean followerAccepted = followRepository.findByFollowerIdAndFollowingIdAndStatus(senderInfo.id(), recipientInfo.id(), "ACCEPTED").isPresent();
        boolean followedByAccepted = followRepository.findByFollowerIdAndFollowingIdAndStatus(recipientInfo.id(), senderInfo.id(), "ACCEPTED").isPresent();

        if (!followerAccepted && !followedByAccepted) {
            var pendingFollow = followRepository.findByFollowerIdAndFollowingId(senderInfo.id(), recipientInfo.id());
            if (pendingFollow.isPresent() && "PENDING".equalsIgnoreCase(pendingFollow.get().getStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your follow request to @" + username + " is pending approval.");
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You must follow @" + username + " and have your request approved to send direct messages.");
        }

        DirectMessage message = messageRepository.save(new DirectMessage(
                senderInfo.id(),
                recipientInfo.id(),
                request.content()
        ));

        try {
            notificationService.createNotification(
                    recipientInfo.id(),
                    recipientInfo.username(),
                    senderInfo.username(),
                    "DM",
                    request.content()
            );
        } catch (Exception e) {
            log.warn("Failed to create message notification: {}", e.getMessage());
        }

        try {
            activityRepository.save(new Activity(
                    recipientInfo.id(),
                    senderInfo.id(),
                    senderInfo.username(),
                    "MESSAGE",
                    request.content()
            ));
        } catch (Exception e) {
            log.warn("Failed to save message activity: {}", e.getMessage());
        }

        return new DirectMessageResponse(
                message.getId(),
                new com.aficio.dto.user.UserSummaryResponse(senderInfo.id(), senderInfo.username()),
                new com.aficio.dto.user.UserSummaryResponse(recipientInfo.id(), recipientInfo.username()),
                message.getContent(),
                message.getSentAt(),
                message.isRead(),
                message.getReadAt()
        );
    }

    @GetMapping("/{username}")
    public List<DirectMessageResponse> conversation(
            @PathVariable String username,
            Authentication authentication
    ) {
        com.aficio.dto.user.UserSummaryResponse current = currentUser(authentication);
        com.aficio.dto.user.UserSummaryResponse targetUser;
        try {
            targetUser = userClient.findByUsername(username);
        } catch (Exception e) {
            targetUser = null;
        }

        if (targetUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account @" + username + " does not exist.");
        }
        final com.aficio.dto.user.UserSummaryResponse other = targetUser;

        return messageRepository.findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderBySentAtAsc(
                current.id(), other.id(), other.id(), current.id()
        ).stream()
                .map(msg -> new DirectMessageResponse(
                        msg.getId(),
                        userForId(msg.getSenderId(), current, other),
                        userForId(msg.getRecipientId(), current, other),
                        msg.getContent(),
                        msg.getSentAt(),
                        msg.isRead(),
                        msg.getReadAt()
                ))
                .collect(java.util.stream.Collectors.toList());
    }

    @PostMapping("/{username}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(
            @PathVariable String username,
            Authentication authentication
    ) {
        com.aficio.dto.user.UserSummaryResponse current = currentUser(authentication);
        com.aficio.dto.user.UserSummaryResponse partner;
        try {
            partner = userClient.findByUsername(username);
        } catch (Exception e) {
            partner = null;
        }

        if (partner == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account @" + username + " does not exist.");
        }

        int updated = messageRepository.markConversationAsRead(current.id(), partner.id());
        if (updated > 0 && messagingTemplate != null) {
            try {
                messagingTemplate.convertAndSendToUser(
                        partner.username(),
                        "/queue/dm-read",
                        new DmReadReceiptResponse(current.username(), LocalDateTime.now())
                );
            } catch (Exception e) {
                log.warn("Failed to notify DM read receipt: {}", e.getMessage());
            }
        }
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Authentication authentication) {
        com.aficio.dto.user.UserSummaryResponse current = currentUser(authentication);
        long total = messageRepository.countByRecipientIdAndIsReadFalse(current.id());
        return new UnreadCountResponse(total);
    }

    private com.aficio.dto.user.UserSummaryResponse userForId(
            Long id,
            com.aficio.dto.user.UserSummaryResponse current,
            com.aficio.dto.user.UserSummaryResponse other
    ) {
        return id.equals(current.id()) ? current : other;
    }

    private com.aficio.dto.user.UserSummaryResponse currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        com.aficio.dto.user.UserSummaryResponse user = userClient.findByUsername(authentication.getName());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found or unauthenticated");
        }
        return user;
    }

    public record MessageRequest(
            @NotBlank(message = "Content is required")
            String content
    ) {
    }

    public record DmRecipientStatusResponse(
            boolean userExists,
            String username,
            Long userId,
            boolean canMessage,
            String followStatus,
            boolean isFollowedBy,
            String reason
    ) {
    }

    public record UnreadCountResponse(long unreadCount) {
    }

    public record DmReadReceiptResponse(String readerUsername, LocalDateTime readAt) {
    }
}
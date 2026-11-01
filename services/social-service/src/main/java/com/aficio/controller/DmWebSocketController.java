package com.aficio.controller;

import com.aficio.activity.Activity;
import com.aficio.activity.ActivityRepository;
import com.aficio.config.UserClient;
import com.aficio.dto.user.UserSummaryResponse;
import com.aficio.message.DirectMessage;
import com.aficio.message.DirectMessageRepository;
import com.aficio.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;

@Controller
public class DmWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(DmWebSocketController.class);

    private final DirectMessageRepository messageRepository;
    private final UserClient userClient;
    private final com.aficio.social.FollowRepository followRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ActivityRepository activityRepository;
    private final NotificationService notificationService;

    public DmWebSocketController(
            DirectMessageRepository messageRepository,
            UserClient userClient,
            com.aficio.social.FollowRepository followRepository,
            SimpMessagingTemplate messagingTemplate,
            ActivityRepository activityRepository,
            NotificationService notificationService
    ) {
        this.messageRepository = messageRepository;
        this.userClient = userClient;
        this.followRepository = followRepository;
        this.messagingTemplate = messagingTemplate;
        this.activityRepository = activityRepository;
        this.notificationService = notificationService;
    }

    @MessageMapping("/dm.send")
    public void sendDm(@Valid @RequestBody DmRequest request, org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        UserSummaryResponse senderInfo = userClient.findByUsername(authentication.getName());
        UserSummaryResponse recipientInfo;
        try {
            recipientInfo = userClient.findByUsername(request.recipientUsername());
        } catch (Exception e) {
            recipientInfo = null;
        }

        if (recipientInfo == null || senderInfo == null || senderInfo.id().equals(recipientInfo.id())) {
            return;
        }

        boolean followerAccepted = followRepository.findByFollowerIdAndFollowingIdAndStatus(senderInfo.id(), recipientInfo.id(), "ACCEPTED").isPresent();
        boolean followedByAccepted = followRepository.findByFollowerIdAndFollowingIdAndStatus(recipientInfo.id(), senderInfo.id(), "ACCEPTED").isPresent();
        if (!followerAccepted && !followedByAccepted) {
            return;
        }

        DirectMessage message = messageRepository.save(new DirectMessage(
                senderInfo.id(),
                recipientInfo.id(),
                request.content()
        ));

        try {
            activityRepository.save(new Activity(
                    recipientInfo.id(),
                    senderInfo.id(),
                    senderInfo.username(),
                    "MESSAGE",
                    request.content()
            ));
        } catch (Exception e) {
            log.warn("Failed to save DM activity: {}", e.getMessage());
        }

        try {
            notificationService.createNotification(
                    recipientInfo.id(),
                    recipientInfo.username(),
                    senderInfo.username(),
                    "DM",
                    request.content()
            );
        } catch (Exception e) {
            log.warn("Failed to create DM notification: {}", e.getMessage());
        }

        messagingTemplate.convertAndSendToUser(
                recipientInfo.username(),
                "/queue/dms",
                new DmResponse(message.getId(), senderInfo.username(), request.content(), message.getSentAt(), message.isRead(), message.getReadAt())
        );
    }

    @MessageMapping("/dm.read")
    public void markRead(@Valid @RequestBody DmReadRequest request, org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return;
        UserSummaryResponse current = userClient.findByUsername(authentication.getName());
        UserSummaryResponse partner;
        try {
            partner = userClient.findByUsername(request.senderUsername());
        } catch (Exception e) {
            partner = null;
        }
        if (current == null || partner == null) return;

        int updated = messageRepository.markConversationAsRead(current.id(), partner.id());
        if (updated > 0) {
            messagingTemplate.convertAndSendToUser(
                    partner.username(),
                    "/queue/dm-read",
                    new DmReadReceipt(current.username(), LocalDateTime.now())
            );
        }
    }

    public record DmRequest(
            @NotBlank(message = "Recipient username is required")
            String recipientUsername,

            @NotBlank(message = "Content is required")
            String content
    ) {
    }

    public record DmReadRequest(
            @NotBlank(message = "Sender username is required")
            String senderUsername
    ) {
    }

    public record DmReadReceipt(
            String readerUsername,
            LocalDateTime readAt
    ) {
    }

    public record DmResponse(
            Long id,
            String senderUsername,
            String content,
            LocalDateTime sentAt,
            boolean isRead,
            LocalDateTime readAt
    ) {
    }
}
package com.aficio.activity;

import java.util.List;

import com.aficio.config.UserClient;
import com.aficio.dto.user.UserSummaryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feed")
public class ActivityController {

    private final ActivityRepository activityRepository;
    private final UserClient userClient;

    public ActivityController(ActivityRepository activityRepository, UserClient userClient) {
        this.activityRepository = activityRepository;
        this.userClient = userClient;
    }

    @GetMapping
    public ResponseEntity<List<ActivityResponse>> feed(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = currentUserId(authentication);
        Pageable pageable = PageRequest.of(page, size);
        List<ActivityResponse> activities = activityRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .stream()
                .map(ActivityResponse::from)
                .toList();
        return ResponseEntity.ok(activities);
    }

    private Long currentUserId(Authentication authentication) {
        UserSummaryResponse userInfo = userClient.findByUsername(authentication.getName());
        return userInfo.id();
    }
}
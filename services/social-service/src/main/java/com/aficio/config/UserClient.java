package com.aficio.config;

import com.aficio.dto.user.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "auth-service",
        url = "${aficio.auth.service-url:http://localhost:8081}",
        fallback = UserClient.UserClientFallback.class
)
public interface UserClient {

    @GetMapping("/users/{username}")
    UserSummaryResponse findByUsername(@PathVariable String username);

    @GetMapping("/users/id/{id}")
    UserSummaryResponse findById(@PathVariable Long id);

    class UserClientFallback implements UserClient {
        @Override
        public UserSummaryResponse findByUsername(String username) {
            return null;
        }

        @Override
        public UserSummaryResponse findById(Long id) {
            return null;
        }
    }
}
package com.aficio.config;

import com.aficio.dto.user.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service")
public interface UserClient {

    @GetMapping("/users/{username}")
    UserSummaryResponse findByUsername(@PathVariable String username);
}
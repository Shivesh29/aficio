package com.aficio.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@ConditionalOnProperty(name = "aficio.redis.enabled", havingValue = "true")
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String KEY_PREFIX = "rate-limit:";
    private static final long MAX_REQUESTS = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RedisTemplate<String, Object> redisTemplate;

    public RateLimitService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String key) {
        try {
            String fullKey = KEY_PREFIX + key;
            Long count = redisTemplate.opsForValue().increment(fullKey);
            if (count == 1) {
                redisTemplate.expire(fullKey, WINDOW);
            }
            boolean allowed = count <= MAX_REQUESTS;
            if (!allowed) {
                log.warn("Rate limit exceeded for key: {}, count: {}", key, count);
            }
            return allowed;
        } catch (Exception e) {
            log.warn("Redis unavailable for rate limiting, allowing request: {}", e.getMessage());
            return true;
        }
    }
}
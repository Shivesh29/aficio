package com.aficio.track;

import com.aficio.dto.track.QuotaAuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks and audits system YouTube Data API quota usage in real-time.
 * Synchronized with Google Cloud API quotas (which reset daily at 00:00 Pacific Time).
 */
@Component
public class SystemQuotaAuditTracker {

    private static final Logger log = LoggerFactory.getLogger(SystemQuotaAuditTracker.class);
    private static final ZoneId PACIFIC_ZONE = ZoneId.of("America/Los_Angeles");
    public static final int DAILY_SEARCHES_LIMIT = 100;
    public static final int DAILY_UNITS_LIMIT = 10000;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // In-memory fallback (resets daily based on Pacific Time date)
    private volatile String memDate = LocalDate.now(PACIFIC_ZONE).toString();
    private final AtomicInteger memSearchesToday = new AtomicInteger(0);
    private final AtomicInteger memUnitsToday = new AtomicInteger(0);
    private final List<QuotaAuditEvent> memAuditEvents = new CopyOnWriteArrayList<>();

    // Per-request accumulator for real-time response payload
    private static final ThreadLocal<RequestQuotaAccumulator> REQUEST_TRACKER = ThreadLocal.withInitial(RequestQuotaAccumulator::new);

    public SystemQuotaAuditTracker(
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        this.objectMapper = objectMapper;
    }

    public static class RequestQuotaAccumulator {
        private int units = 0;
        private boolean active = false;
        private final List<String> details = new ArrayList<>();

        public void reset() {
            units = 0;
            active = false;
            details.clear();
        }

        public void add(int u, String desc) {
            if (active) {
                units += u;
                if (desc != null && !desc.isBlank()) {
                    details.add(desc);
                }
            }
        }

        public int getUnits() {
            return units;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean a) {
            this.active = a;
        }

        public String getSummary() {
            if (details.isEmpty()) {
                return null;
            }
            return String.join("; ", details);
        }
    }

    public void startRequestTracking() {
        RequestQuotaAccumulator acc = REQUEST_TRACKER.get();
        if (!acc.isActive()) {
            acc.reset();
            acc.setActive(true);
        }
    }

    public int getRequestUnits() {
        return REQUEST_TRACKER.get().getUnits();
    }

    public String getRequestDetails() {
        return REQUEST_TRACKER.get().getSummary();
    }

    public void finishRequestTracking() {
        RequestQuotaAccumulator acc = REQUEST_TRACKER.get();
        acc.reset();
    }

    public void clearRequestTracking() {
        REQUEST_TRACKER.remove();
    }

    private String getPtDate() {
        return LocalDate.now(PACIFIC_ZONE).toString();
    }

    private String getSearchesKey() {
        return "aficio:quota:system:searches:" + getPtDate();
    }

    private String getUnitsKey() {
        return "aficio:quota:system:units:" + getPtDate();
    }

    private String getAuditLogKey() {
        return "aficio:quota:system:audit_log:" + getPtDate();
    }

    private void checkInMemoryDailyReset() {
        String today = getPtDate();
        if (!today.equals(memDate)) {
            synchronized (this) {
                if (!today.equals(memDate)) {
                    memDate = today;
                    memSearchesToday.set(0);
                    memUnitsToday.set(0);
                    memAuditEvents.clear();
                }
            }
        }
    }

    /**
     * Records a YouTube Data API call in real time.
     */
    public void recordEvent(
            String action,
            String target,
            String query,
            int units,
            boolean isSearchQuery,
            boolean success,
            String outcomeDetails
    ) {
        String timestamp = ZonedDateTime.now(PACIFIC_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        QuotaAuditEvent event = new QuotaAuditEvent(
                timestamp,
                action,
                target,
                query,
                units,
                isSearchQuery,
                success,
                outcomeDetails
        );

        // Track for the current HTTP request
        REQUEST_TRACKER.get().add(units, action + " (" + units + " units) for [" + target + "]: " + (success ? "Success" : "Failed"));

        // Persist to Redis
        if (redisTemplate != null) {
            try {
                String searchesKey = getSearchesKey();
                String unitsKey = getUnitsKey();
                String logKey = getAuditLogKey();

                if (isSearchQuery) {
                    redisTemplate.opsForValue().increment(searchesKey);
                    redisTemplate.expire(searchesKey, Duration.ofDays(3));
                }

                redisTemplate.opsForValue().increment(unitsKey, units);
                redisTemplate.expire(unitsKey, Duration.ofDays(3));

                String json = objectMapper.writeValueAsString(event);
                redisTemplate.opsForList().leftPush(logKey, json);
                redisTemplate.opsForList().trim(logKey, 0, 99); // Keep latest 100 events
                redisTemplate.expire(logKey, Duration.ofDays(3));

                log.info("[QUOTA-AUDIT] Recorded {} ({} units) | Target: [{}] | Query: [{}] | Success: {} | Details: [{}]",
                        action, units, target, query, success, outcomeDetails);
                return;
            } catch (Exception ex) {
                log.warn("Redis error recording quota audit event: {}", ex.getMessage());
            }
        }

        // In-memory fallback
        checkInMemoryDailyReset();
        if (isSearchQuery) {
            memSearchesToday.incrementAndGet();
        }
        memUnitsToday.addAndGet(units);
        memAuditEvents.add(0, event);
        if (memAuditEvents.size() > 100) {
            memAuditEvents.remove(memAuditEvents.size() - 1);
        }
    }

    public int getSearchesToday() {
        if (redisTemplate != null) {
            try {
                String val = redisTemplate.opsForValue().get(getSearchesKey());
                if (val != null) {
                    return Integer.parseInt(val.trim());
                }
                return 0; // Fresh day with 0 queries so far
            } catch (Exception ex) {
                log.warn("Redis error fetching searches count: {}", ex.getMessage());
            }
        }
        checkInMemoryDailyReset();
        return memSearchesToday.get();
    }

    public int getUnitsToday() {
        if (redisTemplate != null) {
            try {
                String val = redisTemplate.opsForValue().get(getUnitsKey());
                if (val != null) {
                    return Integer.parseInt(val.trim());
                }
                return 0; // Fresh day with 0 units so far
            } catch (Exception ex) {
                log.warn("Redis error fetching units count: {}", ex.getMessage());
            }
        }
        checkInMemoryDailyReset();
        return memUnitsToday.get();
    }

    public List<QuotaAuditEvent> getRecentEvents() {
        if (redisTemplate != null) {
            try {
                List<String> rawList = redisTemplate.opsForList().range(getAuditLogKey(), 0, 49);
                if (rawList != null && !rawList.isEmpty()) {
                    List<QuotaAuditEvent> list = new ArrayList<>();
                    for (String json : rawList) {
                        try {
                            list.add(objectMapper.readValue(json, QuotaAuditEvent.class));
                        } catch (Exception ignored) {}
                    }
                    return list;
                }
                return Collections.emptyList();
            } catch (Exception ex) {
                log.warn("Redis error fetching audit events: {}", ex.getMessage());
            }
        }
        checkInMemoryDailyReset();
        return new ArrayList<>(memAuditEvents);
    }
}

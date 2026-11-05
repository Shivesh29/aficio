package com.aficio.musicbrainz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MusicBrainzClient {

    private static final Logger log = LoggerFactory.getLogger(MusicBrainzClient.class);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final long MIN_REQUEST_INTERVAL_MS = 1000L; // MusicBrainz 1 req/sec policy

    private final RestClient restClient;
    private final Object rateLimitLock = new Object();
    private long lastRequestTime = 0;

    public MusicBrainzClient(
            @Value("${aficio.musicbrainz.user-agent:AficioMusic/1.0.0 (https://github.com/aficio/aficio)}") String userAgent
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .baseUrl("https://musicbrainz.org/ws/2")
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    private void throttle() {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRequestTime;
            if (elapsed < MIN_REQUEST_INTERVAL_MS) {
                try {
                    Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsed);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestTime = System.currentTimeMillis();
        }
    }

    private <T> T executeWithRetry(String operationDescription, Supplier<T> action) {
        int attempts = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (true) {
            attempts++;
            try {
                throttle();
                return action.get();
            } catch (Exception ex) {
                if (ex instanceof HttpStatusCodeException statusEx && statusEx.getStatusCode().value() == 404) {
                    throw ex;
                }
                if (attempts >= MAX_RETRIES) {
                    log.error("MusicBrainz request failed after {} attempts for [{}]: {}", attempts, operationDescription, ex.getMessage());
                    throw ex;
                }
                log.warn("MusicBrainz request attempt {}/{} failed for [{}]: {}. Retrying in {}ms...",
                        attempts, MAX_RETRIES, operationDescription, ex.getMessage(), backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during MusicBrainz backoff", ie);
                }
                backoff *= 2;
            }
        }
    }

    @Cacheable(value = "musicbrainzArtists", key = "#name")
    public MusicBrainzSearchResponse searchArtists(String name) {
        String query = cleanQuery(name);
        String lucene = query.contains(":") ? query : "artist:\"" + query + "\" OR artist:" + query;
        return executeWithRetry("searchArtists: " + name, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/artist")
                        .queryParam("query", lucene)
                        .queryParam("fmt", "json")
                        .queryParam("limit", 20)
                        .build())
                .retrieve()
                .body(MusicBrainzSearchResponse.class));
    }

    @Cacheable(value = "musicbrainzReleases", key = "#title")
    public MusicBrainzReleaseSearchResponse searchReleases(String title) {
        String luceneQuery = buildReleaseLuceneQuery(title);
        MusicBrainzReleaseSearchResponse response = executeWithRetry("searchReleases: " + title, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/release")
                        .queryParam("query", luceneQuery)
                        .queryParam("fmt", "json")
                        .queryParam("limit", 25)
                        .build())
                .retrieve()
                .body(MusicBrainzReleaseSearchResponse.class));

        if (response != null && response.releases() != null) {
            List<MusicBrainzRelease> validReleases = response.releases().stream()
                    .filter(rel -> rel.title() != null && !rel.title().isBlank())
                    .filter(rel -> rel.trackCount() == null || rel.trackCount() > 0)
                    .toList();
            List<MusicBrainzRelease> sorted = new ArrayList<>(validReleases);
            sorted.sort((a, b) -> {
                boolean officialA = "Official".equalsIgnoreCase(a.status());
                boolean officialB = "Official".equalsIgnoreCase(b.status());
                int scoreA = a.score() != null ? a.score() : 0;
                int scoreB = b.score() != null ? b.score() : 0;

                // Priority 1: High score official releases
                if (officialA && !officialB && scoreA >= 80) return -1;
                if (!officialA && officialB && scoreB >= 80) return 1;

                // Priority 2: Score
                if (scoreA != scoreB) {
                    return Integer.compare(scoreB, scoreA);
                }

                // Priority 3: Earliest release year
                int yA = extractYearForRelease(a);
                int yB = extractYearForRelease(b);
                if (yA != yB) {
                    return Integer.compare(yA, yB);
                }

                // Priority 4: Query text matching artist credit
                String queryLower = title != null ? title.toLowerCase() : "";
                boolean artistMatchA = a.artistCredit() != null && a.artistCredit().stream()
                        .anyMatch(ac -> ac.name() != null && queryLower.contains(ac.name().toLowerCase()));
                boolean artistMatchB = b.artistCredit() != null && b.artistCredit().stream()
                        .anyMatch(ac -> ac.name() != null && queryLower.contains(ac.name().toLowerCase()));
                if (artistMatchA && !artistMatchB) return -1;
                if (!artistMatchA && artistMatchB) return 1;

                // Priority 5: Preferred major markets (GB, US, XW, XE)
                String cA = a.country() != null ? a.country().toUpperCase() : "";
                String cB = b.country() != null ? b.country().toUpperCase() : "";
                boolean mktA = List.of("GB", "US", "XW", "XE").contains(cA);
                boolean mktB = List.of("GB", "US", "XW", "XE").contains(cB);
                if (mktA && !mktB) return -1;
                if (!mktA && mktB) return 1;

                return 0;
            });
            return new MusicBrainzReleaseSearchResponse(response.count(), sorted);
        }

        return response;
    }

    private int extractYearForRelease(MusicBrainzRelease rel) {
        if (rel == null) return 9999;
        int y = extractYear(rel.date());
        if (y != 9999) return y;
        if (rel.releaseGroup() != null) {
            return extractYear(rel.releaseGroup().firstReleaseDate());
        }
        return 9999;
    }

    @Cacheable(value = "musicbrainzReleaseDetails", key = "#releaseId")
    public MusicBrainzReleaseDetails getReleaseDetails(String releaseId) {
        try {
            return executeWithRetry("getReleaseDetails: " + releaseId, () -> restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/release/{id}")
                            .queryParam("inc", "recordings+artist-credits+release-groups")
                            .queryParam("fmt", "json")
                            .build(releaseId))
                    .retrieve()
                    .body(MusicBrainzReleaseDetails.class));
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.info("Release ID [{}] returned 404 from MusicBrainz. Attempting to resolve as Release Group...", releaseId);
                return resolveReleaseGroupAsReleaseDetails(releaseId);
            }
            throw ex;
        }
    }

    @Cacheable(value = "musicbrainzReleaseGroupWithReleases", key = "#releaseGroupId")
    public MusicBrainzReleaseGroup getReleaseGroupWithReleases(String releaseGroupId) {
        return executeWithRetry("getReleaseGroupWithReleases: " + releaseGroupId, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/release-group/{id}")
                        .queryParam("inc", "releases")
                        .queryParam("fmt", "json")
                        .build(releaseGroupId))
                .retrieve()
                .body(MusicBrainzReleaseGroup.class));
    }

    private MusicBrainzReleaseDetails resolveReleaseGroupAsReleaseDetails(String releaseGroupId) {
        try {
            MusicBrainzReleaseGroup rg = getReleaseGroupWithReleases(releaseGroupId);
            if (rg == null || rg.releases() == null || rg.releases().isEmpty()) {
                log.warn("No releases found under release group [{}]", releaseGroupId);
                return null;
            }

            // Pick the best canonical release:
            // 1. Prefer Official status
            // 2. Prefer major markets (US, GB, XW, XE)
            // 3. Fallback to first available release
            MusicBrainzRelease canonicalRelease = rg.releases().stream()
                    .sorted((a, b) -> {
                        boolean offA = "Official".equalsIgnoreCase(a.status());
                        boolean offB = "Official".equalsIgnoreCase(b.status());
                        if (offA && !offB) return -1;
                        if (!offA && offB) return 1;

                        String cA = a.country() != null ? a.country().toUpperCase() : "";
                        String cB = b.country() != null ? b.country().toUpperCase() : "";
                        boolean mktA = List.of("US", "GB", "XW", "XE").contains(cA);
                        boolean mktB = List.of("US", "GB", "XW", "XE").contains(cB);
                        if (mktA && !mktB) return -1;
                        if (!mktA && mktB) return 1;

                        return 0;
                    })
                    .findFirst()
                    .orElse(null);

            if (canonicalRelease == null || canonicalRelease.id() == null) {
                return null;
            }

            log.info("Resolved Release-Group ID [{}] to canonical Release ID [{}] ({})",
                    releaseGroupId, canonicalRelease.id(), canonicalRelease.title());
            return getReleaseDetails(canonicalRelease.id());
        } catch (Exception ex) {
            log.warn("Failed to resolve release group [{}] as release details: {}", releaseGroupId, ex.getMessage());
            return null;
        }
    }

    @Cacheable(value = "musicbrainzRecordings", key = "#title")
    public MusicBrainzRecordingSearchResponse searchRecordings(String title) {
        String lucene = buildRecordingLuceneQuery(title);
        return executeWithRetry("searchRecordings: " + title, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/recording")
                        .queryParam("query", lucene)
                        .queryParam("fmt", "json")
                        .queryParam("limit", 20)
                        .build())
                .retrieve()
                .body(MusicBrainzRecordingSearchResponse.class));
    }

    @Cacheable(value = "musicbrainzRecordingDetails", key = "#recordingMbid")
    public MusicBrainzRecording getRecordingDetails(String recordingMbid) {
        return executeWithRetry("getRecordingDetails: " + recordingMbid, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/recording/{id}")
                        .queryParam("inc", "url-rels")
                        .queryParam("fmt", "json")
                        .build(recordingMbid))
                .retrieve()
                .body(MusicBrainzRecording.class));
    }

    @Cacheable(value = "musicbrainzReleaseGroupDetails", key = "#releaseGroupId")
    public MusicBrainzReleaseGroup getReleaseGroupDetails(String releaseGroupId) {
        return executeWithRetry("getReleaseGroupDetails: " + releaseGroupId, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/release-group/{id}")
                        .queryParam("inc", "url-rels")
                        .queryParam("fmt", "json")
                        .build(releaseGroupId))
                .retrieve()
                .body(MusicBrainzReleaseGroup.class));
    }

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b");

    private int extractYear(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return 9999;
        }
        Matcher m = YEAR_PATTERN.matcher(dateStr);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 9999;
    }

    private String normalizeReleaseTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        // Strip common edition and reboot tags in parentheses or brackets:
        // (Deluxe Edition), [Remastered], (30th Anniversary Super Deluxe), (Expanded Edition), etc.
        String cleaned = title.replaceAll("(?i)[\\(\\[\\{].*?(anniversary|deluxe|remaster|expanded|edition|reissue|bonus|special|box\\s*set|super\\s*deluxe).*?[\\)\\]\\}]", "");
        cleaned = cleaned.replaceAll("(?i)\\s*-\\s*(anniversary|deluxe|remaster|expanded|edition|reissue|bonus|special).*", "");
        cleaned = cleaned.trim().replaceAll("\\s+", " ").toLowerCase();
        return cleaned.isEmpty() ? title.trim().toLowerCase() : cleaned;
    }

    @Cacheable(value = "musicbrainzArtistReleases", key = "#artistMbid")
    public MusicBrainzReleaseSearchResponse getArtistReleases(String artistMbid) {
        String cleanMbid = cleanQuery(artistMbid);
        String lucene = "arid:" + cleanMbid + " AND (primarytype:album OR primarytype:single OR primarytype:ep) AND status:official";
        List<MusicBrainzRelease> allReleases = new ArrayList<>();

        MusicBrainzReleaseSearchResponse page1 = fetchReleases(lucene, 0, 100);
        if (page1 != null && page1.releases() != null && !page1.releases().isEmpty()) {
            allReleases.addAll(page1.releases());
            // If artist has > 100 releases, fetch page 2 to ensure earlier catalog releases are found
            if (page1.count() > 100) {
                MusicBrainzReleaseSearchResponse page2 = fetchReleases(lucene, 100, 100);
                if (page2 != null && page2.releases() != null) {
                    allReleases.addAll(page2.releases());
                }
            }
        } else {
            MusicBrainzReleaseSearchResponse fallback = fetchReleases("arid:" + cleanMbid, 0, 100);
            if (fallback != null && fallback.releases() != null) {
                allReleases.addAll(fallback.releases());
            }
        }

        if (allReleases.isEmpty()) {
            return new MusicBrainzReleaseSearchResponse(0, Collections.emptyList());
        }

        // Pre-calculate earliest valid release year per release group among releases with valid tracks
        Map<String, Integer> earliestYearByRg = new HashMap<>();
        for (MusicBrainzRelease rel : allReleases) {
            int trackCount = rel.trackCount() != null ? rel.trackCount() : 0;
            if (trackCount < 1) {
                continue; // Skip releases with 0 tracks
            }
            MusicBrainzReleaseGroup rg = rel.releaseGroup();
            String pType = rg != null && rg.primaryType() != null ? rg.primaryType() : "Album";
            if ("Album".equalsIgnoreCase(pType) && trackCount < 4) {
                continue; // Broken album stub
            }
            String key = (rg != null && rg.id() != null)
                    ? rg.id()
                    : normalizeReleaseTitle(rel.title()) + "::" + pType.toLowerCase();
            int year = extractYear(rel.date());
            if (year > 1900 && year < 9999) {
                earliestYearByRg.merge(key, year, Math::min);
            }
        }

        // Group releases by release group ID (fallback to normalized title):
        Map<String, MusicBrainzRelease> bestByRg = new LinkedHashMap<>();
        Map<String, Integer> bestScoreByRg = new HashMap<>();

        for (MusicBrainzRelease rel : allReleases) {
            int trackCount = rel.trackCount() != null ? rel.trackCount() : 0;
            if (trackCount < 1) {
                continue;
            }
            MusicBrainzReleaseGroup rg = rel.releaseGroup();
            String pType = rg != null && rg.primaryType() != null ? rg.primaryType() : "Album";
            if ("Album".equalsIgnoreCase(pType) && trackCount < 4) {
                continue;
            }
            String key = (rg != null && rg.id() != null)
                    ? rg.id()
                    : normalizeReleaseTitle(rel.title()) + "::" + pType.toLowerCase();

            int earliestYear = earliestYearByRg.getOrDefault(key, 9999);
            int score = scoreRelease(rel, rg, earliestYear);

            if (!bestByRg.containsKey(key) || score > bestScoreByRg.get(key)) {
                bestByRg.put(key, rel);
                bestScoreByRg.put(key, score);
            }
        }

        List<MusicBrainzRelease> sortedList = new ArrayList<>(bestByRg.values());
        // Sort chronologically by original release date ascending
        sortedList.sort((a, b) -> {
            MusicBrainzReleaseGroup rgA = a.releaseGroup();
            MusicBrainzReleaseGroup rgB = b.releaseGroup();
            String dateA = (rgA != null && rgA.firstReleaseDate() != null && !rgA.firstReleaseDate().isBlank())
                    ? rgA.firstReleaseDate()
                    : (a.date() != null ? a.date() : "9999");
            String dateB = (rgB != null && rgB.firstReleaseDate() != null && !rgB.firstReleaseDate().isBlank())
                    ? rgB.firstReleaseDate()
                    : (b.date() != null ? b.date() : "9999");

            int yA = extractYear(dateA);
            int yB = extractYear(dateB);
            if (yA != yB) {
                return Integer.compare(yA, yB);
            }
            return dateA.compareTo(dateB);
        });

        return new MusicBrainzReleaseSearchResponse(sortedList.size(), sortedList);
    }

    private int scoreRelease(MusicBrainzRelease release, MusicBrainzReleaseGroup rg, int earliestYearInGroup) {
        int trackCount = release.trackCount() != null ? release.trackCount() : 0;
        if (trackCount < 1) {
            return -10000;
        }
        String pType = rg != null && rg.primaryType() != null ? rg.primaryType() : "Album";
        if ("Album".equalsIgnoreCase(pType) && trackCount < 4) {
            return -10000;
        }

        int score = 0;
        int releaseYear = extractYear(release.date());
        int firstYear = (earliestYearInGroup > 1900 && earliestYearInGroup < 9999)
                ? earliestYearInGroup
                : (rg != null ? extractYear(rg.firstReleaseDate()) : 9999);

        // 1. Proximity to original / earliest release date:
        // Heavily penalize releases years after the earliest release date (e.g. 20-year anniversary reboots)
        if (firstYear != 9999 && releaseYear != 9999) {
            int yearDiff = Math.abs(releaseYear - firstYear);
            score -= yearDiff * 60;
            if (yearDiff == 0) {
                score += 200; // Reward for original release year pressing
            }
        }

        // Exact match with first release date (e.g. "1970-02-13")
        String firstDate = rg != null && rg.firstReleaseDate() != null ? rg.firstReleaseDate() : "";
        String relDate = release.date() != null ? release.date() : "";
        if (!firstDate.isBlank() && !relDate.isBlank()) {
            if (relDate.startsWith(firstDate) || firstDate.startsWith(relDate)) {
                score += 150;
            } else if (firstDate.length() >= 4 && relDate.startsWith(firstDate.substring(0, 4))) {
                score += 80;
            }
        }

        // 2. Official status
        if ("Official".equalsIgnoreCase(release.status())) {
            score += 150;
        }

        // 3. Penalize deluxe editions, anniversary reboots, box sets in title
        String titleLower = release.title() != null ? release.title().toLowerCase() : "";
        for (String kw : List.of("deluxe", "anniversary", "box set", "super deluxe", "expanded", "remaster", "reissue", "bonus")) {
            if (titleLower.contains(kw)) {
                score -= 100;
            }
        }

        // Penalize releases with compound slash medleys in title (e.g. vinyl mashups)
        if (titleLower.contains(" / ")) {
            score -= 150;
        }

        // 4. Prefer original major Western / global markets (GB, US, XW, XE) over regional import pressings
        String country = release.country() != null ? release.country().toUpperCase() : "";
        if (List.of("GB", "US", "XW", "XE").contains(country)) {
            score += 40;
        } else if ("JP".equals(country)) {
            score += 10;
        }

        // 5. Mild tie-breaker for date precision within the same year
        if (relDate.length() >= 4) {
            score += Math.min(relDate.length(), 10);
        }

        // 6. Prefer standard studio album track count (7 to 18 tracks), penalize extreme bloat (>25)
        if ("Album".equalsIgnoreCase(pType)) {
            if (trackCount >= 7 && trackCount <= 18) {
                score += 50;
            } else if (trackCount > 25) {
                score -= (trackCount - 25) * 10;
            }
        }

        return score;
    }


    @Cacheable(value = "musicbrainzArtistDetails", key = "#artistMbid")
    public MusicBrainzArtist getArtistDetails(String artistMbid) {
        return executeWithRetry("getArtistDetails: " + artistMbid, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/artist/{id}")
                        .queryParam("fmt", "json")
                        .build(artistMbid))
                .retrieve()
                .body(MusicBrainzArtist.class));
    }

    private MusicBrainzReleaseSearchResponse fetchReleases(String luceneQuery, int offset, int limit) {
        try {
            return executeWithRetry("fetchReleases: " + luceneQuery + " offset=" + offset, () -> restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/release")
                            .queryParam("query", luceneQuery)
                            .queryParam("fmt", "json")
                            .queryParam("offset", offset)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(MusicBrainzReleaseSearchResponse.class));
        } catch (Exception e) {
            log.warn("Failed to fetch releases for query [{}]: {}", luceneQuery, e.getMessage());
            return null;
        }
    }

    private String cleanQuery(String input) {
        if (input == null) return "";
        return input.trim().replace("\"", "\\\"");
    }

    private String buildReleaseLuceneQuery(String input) {
        if (input == null || input.isBlank()) return "status:official";
        String trimmed = input.trim();

        // If user already specified Lucene fields
        if (trimmed.contains("release:") || trimmed.contains("artist:")) {
            return trimmed;
        }

        // Check for "by" pattern: e.g. "Loveless by My Bloody Valentine"
        if (trimmed.matches("(?i).+\\s+by\\s+.+")) {
            String[] parts = trimmed.split("(?i)\\s+by\\s+", 2);
            String album = cleanQuery(parts[0]);
            String artist = cleanQuery(parts[1]);
            return String.format("(release:\"%s\" OR release:%s) AND (artist:\"%s\" OR artist:%s)",
                    album, album, artist, artist);
        }

        // Check for "-" pattern: e.g. "My Bloody Valentine - Loveless"
        if (trimmed.contains(" - ")) {
            String[] parts = trimmed.split("\\s+-\\s+", 2);
            String p1 = cleanQuery(parts[0]);
            String p2 = cleanQuery(parts[1]);
            return String.format("((release:\"%s\" AND artist:\"%s\") OR (release:\"%s\" AND artist:\"%s\"))",
                    p2, p1, p1, p2);
        }

        String cleaned = cleanQuery(trimmed);
        String[] words = cleaned.split("\\s+");

        if (words.length >= 2) {
            // First word might be album, rest artist: "loveless my bloody valentine"
            String firstWord = words[0];
            String rest = cleaned.substring(firstWord.length()).trim();

            // Or last word might be album, first words artist: "my bloody valentine loveless"
            String lastWord = words[words.length - 1];
            String firstWords = cleaned.substring(0, cleaned.length() - lastWord.length()).trim();

            return String.format(
                    "((release:\"%s\"^4) OR (release:\"%s\" AND artist:\"%s\"^3) OR (artist:\"%s\" AND release:\"%s\"^3) OR (\"%s\"^2) OR (%s))",
                    cleaned, firstWord, rest, firstWords, lastWord, cleaned, String.join(" AND ", words)
            );
        }

        // Single word or simple query: e.g. "loveless"
        return String.format("(release:\"%s\"^3 OR release:%s*^2 OR \"%s\")", cleaned, cleaned, cleaned);
    }

    private String buildRecordingLuceneQuery(String input) {
        if (input == null || input.isBlank()) return "";
        String trimmed = input.trim();
        if (trimmed.contains("recording:") || trimmed.contains("artist:")) {
            return trimmed;
        }

        if (trimmed.matches("(?i).+\\s+by\\s+.+")) {
            String[] parts = trimmed.split("(?i)\\s+by\\s+", 2);
            String track = cleanQuery(parts[0]);
            String artist = cleanQuery(parts[1]);
            return String.format("(recording:\"%s\" OR recording:%s) AND (artist:\"%s\" OR artist:%s)",
                    track, track, artist, artist);
        }

        String cleaned = cleanQuery(trimmed);
        return String.format("(recording:\"%s\"^2 OR recording:%s)", cleaned, cleaned);
    }
}
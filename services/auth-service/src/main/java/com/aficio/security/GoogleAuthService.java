package com.aficio.security;

import com.aficio.controller.UserController.LoginResponse;
import com.aficio.user.User;
import com.aficio.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;

@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String configuredClientId;

    public GoogleAuthService(
            UserRepository userRepository,
            JwtService jwtService,
            ObjectMapper objectMapper,
            @Value("${aficio.oauth.google.client-id:}") String configuredClientId
    ) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.configuredClientId = (configuredClientId != null) ? configuredClientId.trim() : "";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Transactional
    public LoginResponse authenticate(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google ID token is required");
        }

        JsonNode payload = verifyGoogleToken(idToken.trim());
        String email = payload.path("email").asText();
        boolean emailVerified = payload.path("email_verified").asBoolean(false)
                || "true".equalsIgnoreCase(payload.path("email_verified").asText());
        String sub = payload.path("sub").asText();
        String name = payload.has("name") && !payload.get("name").isNull() ? payload.get("name").asText().trim() : null;
        String picture = payload.has("picture") && !payload.get("picture").isNull() ? payload.get("picture").asText().trim() : null;

        if (email == null || email.isBlank() || sub == null || sub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token missing required email or subject");
        }

        if (!emailVerified) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google account email is not verified");
        }

        // 1. Check if user already exists with this Google sub ID
        User user = userRepository.findByGoogleId(sub)
                .orElse(null);

        // 2. If not found, check if user exists with this email (Auto-link existing local account)
        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(email)
                    .orElse(null);

            if (user != null) {
                log.info("Auto-linking existing account [{}] with Google ID [{}]", user.getUsername(), sub);
                user.setGoogleId(sub);
                user.setEmailVerified(true);
                if (user.getAvatarUrl() == null && picture != null) {
                    user.setAvatarUrl(picture);
                }
                user = userRepository.save(user);
            }
        }

        // 3. Brand new user registration via Google
        if (user == null) {
            String username = generateUniqueUsername(name, email);
            user = new User(username, email, null);
            user.setAuthProvider("GOOGLE");
            user.setGoogleId(sub);
            user.setEmailVerified(true);
            user.setEnabled(true);
            if (name != null && !name.isBlank()) {
                user.setDisplayName(name);
            }
            if (picture != null && !picture.isBlank()) {
                user.setAvatarUrl(picture);
            }
            user = userRepository.save(user);
            log.info("Registered new Google OAuth user [{}] ({})", user.getUsername(), user.getEmail());
        }

        // 4. Handle 2FA if enabled
        if (user.isTwoFactorEnabled()) {
            String tempToken = jwtService.generateToken(user.getUsername(), user.getEmail(), "2fa");
            return new LoginResponse(tempToken, true);
        }

        // 5. Issue standard Aficio JWT
        String accessToken = jwtService.generateToken(user.getUsername(), user.getEmail(), "access");
        return new LoginResponse(accessToken, false);
    }

    private JsonNode verifyGoogleToken(String idToken) {
        try {
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token="
                    + URLEncoder.encode(idToken, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                log.warn("Google tokeninfo returned HTTP {}: {}", response.statusCode(), response.body());
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired Google token");
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token verification error: " + root.path("error").asText());
            }

            // Verify audience against configured client ID (if configured)
            if (!configuredClientId.isBlank()) {
                String aud = root.path("aud").asText();
                if (!configuredClientId.equals(aud)) {
                    log.error("Google token audience [{}] does not match configured client ID [{}]", aud, configuredClientId);
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token audience mismatch");
                }
            }

            return root;
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.error("Error communicating with Google token verification endpoint: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to verify Google token with Google servers");
        }
    }

    private String generateUniqueUsername(String name, String email) {
        String base = (name != null && !name.isBlank()) ? name : email.split("@")[0];
        base = base.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
        if (base.isBlank()) {
            base = "aficionado";
        }
        if (base.length() > 20) {
            base = base.substring(0, 20);
        }

        String candidate = base;
        Random random = new Random();
        while (userRepository.existsByUsername(candidate)) {
            int suffix = 1000 + random.nextInt(9000);
            candidate = base + "_" + suffix;
            if (candidate.length() > 30) {
                candidate = base.substring(0, 20) + "_" + suffix;
            }
        }
        return candidate;
    }
}

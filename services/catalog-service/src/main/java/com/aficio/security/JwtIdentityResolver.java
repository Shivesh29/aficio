package com.aficio.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtIdentityResolver.class);
    private final SecretKey signingKey;
    private final Set<String> ownerEmails;

    public JwtIdentityResolver(
            @Value("${aficio.jwt.secret}") String secret,
            @Value("${aficio.owner-emails:}") String ownerEmailsConfig
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ownerEmails = Arrays.stream(ownerEmailsConfig.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * Cryptographically verifies the Authorization Bearer JWT.
     * Raw spoofable headers (e.g. X-User-Username) are rejected for security and privilege checks.
     */
    public VerifiedIdentity resolve(HttpServletRequest request) {
        if (request == null) {
            return VerifiedIdentity.anonymous("unknown");
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String username = claims.getSubject();
                String email = claims.get("email", String.class);
                boolean isDev = email != null && ownerEmails.contains(email.trim().toLowerCase());
                log.debug("Verified JWT identity for user [{}], email [{}], isDev [{}]", username, email, isDev);
                return VerifiedIdentity.authenticated(username, email, isDev);
            } catch (Exception ex) {
                log.debug("JWT verification failed or expired: {}", ex.getMessage());
            }
        }

        // For unauthenticated requests, extract client IP for rate-limiting
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = (forwardedFor != null && !forwardedFor.isBlank())
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();

        return VerifiedIdentity.anonymous(ip);
    }
}

package com.aficio.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;

    public JwtService(
            @Value("${aficio.jwt.secret}") String secret
    ) {
        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateToken(String username, String email, String purpose) {
        Date now = new Date();

        Date expiration = new Date(
                now.getTime() + 1000L * 60 * 60 * 24
        );

        var builder = Jwts.builder()
                .subject(username)
                .claim("purpose", purpose)
                .issuedAt(now)
                .expiration(expiration);

        if (email != null && !email.isBlank()) {
            builder.claim("email", email);
        }

        return builder.signWith(signingKey).compact();
    }

    public String generateToken(String username, String purpose) {
        return generateToken(username, null, purpose);
    }

    public String generateToken(String username) {
        return generateToken(username, null, "access");
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String extractPurpose(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("purpose", String.class);
    }
}
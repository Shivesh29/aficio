package com.aficio.security;

/**
 * Represents a cryptographically verified user identity extracted from a signed JWT.
 * Guarantees that dev/owner privileges cannot be spoofed via raw HTTP headers.
 */
public record VerifiedIdentity(
        String username,
        String email,
        boolean isDevAccount,
        boolean isAuthenticated
) {
    public static VerifiedIdentity anonymous(String ipAddress) {
        return new VerifiedIdentity("anonymous", null, false, false);
    }

    public static VerifiedIdentity authenticated(String username, String email, boolean isDevAccount) {
        return new VerifiedIdentity(username, email, isDevAccount, true);
    }

    public static VerifiedIdentity authenticated(String username, String email) {
        return new VerifiedIdentity(username, email, false, true);
    }

    public String toClientId(String fallbackIp) {
        if (isAuthenticated) {
            if (email != null && !email.isBlank()) {
                return "user:" + username + ":" + email;
            }
            return "user:" + username;
        }
        return "ip:" + (fallbackIp != null && !fallbackIp.isBlank() ? fallbackIp : "unknown");
    }
}

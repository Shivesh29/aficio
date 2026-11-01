package com.aficio.user;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(
        name = "app_user",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "username"),
                @UniqueConstraint(columnNames = "email")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String email;

    private String passwordHash;

    private boolean enabled = true;

    private boolean emailVerified = true;

    private String verificationToken;

    private java.time.LocalDateTime verificationTokenExpiresAt;

    private boolean twoFactorEnabled = false;

    @jakarta.persistence.Column(name = "secret_2fa")
    private String secret2fa;

    @jakarta.persistence.Column(name = "display_name", length = 100)
    private String displayName;

    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String bio;

    @jakarta.persistence.Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @jakarta.persistence.Column(name = "is_private")
    private boolean isPrivate = false;

    @jakarta.persistence.Column(name = "encrypted_youtube_api_key", columnDefinition = "TEXT")
    private String encryptedYoutubeApiKey;

    @jakarta.persistence.Column(name = "api_key_storage_preference", length = 30)
    private String apiKeyStoragePreference = "NONE";

    @jakarta.persistence.Column(name = "auth_provider", length = 50)
    private String authProvider = "LOCAL";

    @jakarta.persistence.Column(name = "google_id", length = 255)
    private String googleId;

    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @JsonIgnore
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    @JsonIgnore
    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    @JsonIgnore
    public java.time.LocalDateTime getVerificationTokenExpiresAt() {
        return verificationTokenExpiresAt;
    }

    public void setVerificationTokenExpiresAt(java.time.LocalDateTime verificationTokenExpiresAt) {
        this.verificationTokenExpiresAt = verificationTokenExpiresAt;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public void setTwoFactorEnabled(boolean twoFactorEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
    }

    @JsonIgnore
    public String getSecret2fa() {
        return secret2fa;
    }

    public void setSecret2fa(String secret2fa) {
        this.secret2fa = secret2fa;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    @JsonIgnore
    public String getEncryptedYoutubeApiKey() {
        return encryptedYoutubeApiKey;
    }

    public void setEncryptedYoutubeApiKey(String encryptedYoutubeApiKey) {
        this.encryptedYoutubeApiKey = encryptedYoutubeApiKey;
    }

    public String getApiKeyStoragePreference() {
        return apiKeyStoragePreference != null ? apiKeyStoragePreference : "NONE";
    }

    public void setApiKeyStoragePreference(String apiKeyStoragePreference) {
        this.apiKeyStoragePreference = apiKeyStoragePreference;
    }

    public String getAuthProvider() {
        return authProvider != null ? authProvider : "LOCAL";
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }
}
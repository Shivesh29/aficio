package com.aficio.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aficio.dto.user.UserSummaryResponse;
import com.aficio.user.EmailService;
import com.aficio.user.User;
import com.aficio.user.UserRepository;
import com.aficio.security.JwtService;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.util.Utils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@Validated
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final String frontendUrl;
    private final com.aficio.security.AesGcmEncryptionService encryptionService;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final com.aficio.security.GoogleAuthService googleAuthService;
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    public UserController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService,
            @org.springframework.beans.factory.annotation.Value("${aficio.frontend.url}") String frontendUrl,
            com.aficio.security.AesGcmEncryptionService encryptionService,
            org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisTemplateProvider,
            com.aficio.security.GoogleAuthService googleAuthService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
        this.encryptionService = encryptionService;
        this.stringRedisTemplate = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        this.googleAuthService = googleAuthService;
    }

    @PostMapping("/users/register")
    public RegistrationResponse register(
            @Valid @RequestBody RegistrationRequest request
    ) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username is already in use"
            );
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already in use"
            );
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                request.username(),
                request.email(),
                passwordHash
        );

        user.setEnabled(false);
        user.setEmailVerified(false);

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(24));

        User savedUser = userRepository.save(user);

        String verificationUrl = null;
        try {
            verificationUrl = emailService.sendVerificationEmail(savedUser.getId(), savedUser.getEmail(), token);
        } catch (Exception e) {
            log.error("Registration succeeded but verification email failed for user: {}", savedUser.getUsername(), e);
            String fallbackUrl = frontendUrl + "/verify-email?token=" + token;
            log.warn("Verification fallback link for {}: {}", savedUser.getEmail(), fallbackUrl);
            verificationUrl = fallbackUrl;
        }

        return new RegistrationResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                verificationUrl
        );
    }

    @GetMapping("/users/id/{id}")
    public UserSummaryResponse findById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserSummaryResponse(user.getId(), user.getUsername(), user.isPrivate());
    }

    @PostMapping("/users/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        String identifier = request.getIdentifier();
        if (identifier.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username or email is required"
            );
        }

        User user = userRepository
                .findByUsername(identifier)
                .or(() -> userRepository.findByUsernameIgnoreCase(identifier))
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username or password"
                ));

        if (!user.isEmailVerified()) {
            // Automatically resend verification email
            String token = UUID.randomUUID().toString();
            user.setVerificationToken(token);
            user.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(24));
            userRepository.save(user);
            try {
                emailService.sendVerificationEmail(user.getId(), user.getEmail(), token);
            } catch (Exception e) {
                log.error("Failed to auto-resend verification email during login for: {}", user.getUsername(), e);
            }

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please verify your email before logging in. A new verification link has been sent to your inbox."
            );
        }

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid username or password"
            );
        }

        if (user.isTwoFactorEnabled()) {
            String tempToken = jwtService.generateToken(user.getUsername(), user.getEmail(), "2fa");
            return new LoginResponse(tempToken, true);
        }

        String token = jwtService.generateToken(user.getUsername(), user.getEmail(), "access");
        return new LoginResponse(token, false);
    }

    @PostMapping("/users/oauth/google")
    public LoginResponse loginWithGoogle(
            @Valid @RequestBody GoogleOAuthRequest request
    ) {
        return googleAuthService.authenticate(request.idToken());
    }

    @PostMapping("/users/2fa/verify")
    public LoginResponse verify2fa(
            @Valid @RequestBody TwoFactorVerifyRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        }
        String token = authHeader.substring(7);

        try {
            String purpose = jwtService.extractPurpose(token);
            if (!"2fa".equals(purpose)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token purpose");
            }

            String username = jwtService.extractUsername(token);
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

            if (!user.isTwoFactorEnabled() || user.getSecret2fa() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "2FA is not enabled for this user");
            }

            DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
            if (!verifier.isValidCode(user.getSecret2fa(), request.code())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid 2FA code");
            }

            String accessToken = jwtService.generateToken(user.getUsername(), user.getEmail(), "access");
            return new LoginResponse(accessToken, false);

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token or code");
        }
    }

    @PostMapping("/users/2fa/setup")
    public TwoFactorSetupResponse setup2fa(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String secret = new DefaultSecretGenerator().generate();
        user.setSecret2fa(secret);
        userRepository.save(user);

        QrData data = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer("Aficio")
                .algorithm(dev.samstevens.totp.code.HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        ZxingPngQrGenerator qrGenerator = new ZxingPngQrGenerator();
        byte[] imageData;
        try {
            imageData = qrGenerator.generate(data);
        } catch (dev.samstevens.totp.exceptions.QrGenerationException e) {
            throw new RuntimeException("Error generating QR code", e);
        }

        String mimeType = qrGenerator.getImageMimeType();
        String dataUri = Utils.getDataUriForImage(imageData, mimeType);

        return new TwoFactorSetupResponse(secret, dataUri);
    }

    @PostMapping("/users/2fa/enable")
    public void enable2fa(@Valid @RequestBody TwoFactorVerifyRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getSecret2fa() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "2FA not set up");
        }

        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        if (!verifier.isValidCode(user.getSecret2fa(), request.code())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid 2FA code");
        }

        user.setTwoFactorEnabled(true);
        userRepository.save(user);
    }

    @PostMapping("/users/2fa/disable")
    public void disable2fa(@Valid @RequestBody TwoFactorVerifyRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!user.isTwoFactorEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "2FA is already disabled");
        }

        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        if (!verifier.isValidCode(user.getSecret2fa(), request.code())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid 2FA code");
        }

        user.setTwoFactorEnabled(false);
        user.setSecret2fa(null);
        userRepository.save(user);
    }

    @GetMapping("/users/verify-email")
    public VerificationResponse verifyEmail(@RequestParam String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid verification token"
                ));

        if (user.getVerificationTokenExpiresAt() != null && user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Verification token has expired"
            );
        }

        user.setEmailVerified(true);
        user.setEnabled(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);

        userRepository.save(user);

        return new VerificationResponse(true, "Email successfully verified.");
    }

    @PostMapping("/users/resend-verification")
    public VerificationResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No account found with this email address"
                ));

        if (user.isEmailVerified()) {
            return new VerificationResponse(true, "Email is already verified. You can log in.");
        }

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        try {
            emailService.sendVerificationEmail(user.getId(), user.getEmail(), token);
        } catch (Exception e) {
            log.error("Failed to resend verification email to: {}", user.getEmail(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to send verification email. Please try again later."
            );
        }

        return new VerificationResponse(true, "Verification email sent. Please check your inbox.");
    }

    @GetMapping("/users/me")
    public MeResponse currentUser(
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarUrl(),
                user.isPrivate(),
                user.isTwoFactorEnabled()
        );
    }

    @GetMapping("/users/{username}")
    public UserProfileResponse getUserProfile(@PathVariable String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarUrl(),
                user.isPrivate()
        );
    }

    @GetMapping("/users/{username}/privacy")
    public PrivacyResponse getUserPrivacy(@PathVariable String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new PrivacyResponse(user.isPrivate());
    }

    // ──────────────── User Settings Endpoints ────────────────

    @GetMapping("/users/settings")
    public SettingsResponse getSettings(Authentication authentication) {
        User user = requireAuth(authentication);
        return new SettingsResponse(
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarUrl(),
                user.isPrivate(),
                user.isTwoFactorEnabled()
        );
    }

    @PutMapping("/users/settings")
    public SettingsResponse updateSettings(
            @Valid @RequestBody UpdateSettingsRequest request,
            Authentication authentication
    ) {
        User user = requireAuth(authentication);

        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().isBlank() ? null : request.displayName().trim());
        }
        if (request.bio() != null) {
            user.setBio(request.bio().isBlank() ? null : request.bio().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl().trim());
        }
        if (request.isPrivate() != null) {
            user.setPrivate(request.isPrivate());
        }

        userRepository.save(user);

        return new SettingsResponse(
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarUrl(),
                user.isPrivate(),
                user.isTwoFactorEnabled()
        );
    }

    @PutMapping("/users/settings/username")
    public UsernameUpdateResponse updateUsername(
            @Valid @RequestBody UpdateUsernameRequest request,
            Authentication authentication
    ) {
        User user = requireAuth(authentication);
        String oldUsername = user.getUsername();
        String newUsername = request.username() != null ? request.username().trim() : "";

        if (newUsername.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username cannot be empty");
        }

        if (!newUsername.matches("^[a-zA-Z0-9_-]{3,30}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username must be 3-30 characters and contain only letters, numbers, underscores, or hyphens"
            );
        }

        if (newUsername.equals(oldUsername)) {
            String currentToken = jwtService.generateToken(user.getUsername(), user.getEmail(), "access");
            return new UsernameUpdateResponse(user.getUsername(), currentToken, "Username unchanged.");
        }

        if (!newUsername.equalsIgnoreCase(oldUsername) && userRepository.existsByUsername(newUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username @" + newUsername + " is already taken");
        }

        user.setUsername(newUsername);
        User savedUser = userRepository.save(user);

        evictUserKeyCache(oldUsername);

        String newToken = jwtService.generateToken(savedUser.getUsername(), savedUser.getEmail(), "access");

        log.info("User {} renamed to {}", oldUsername, newUsername);
        return new UsernameUpdateResponse(savedUser.getUsername(), newToken, "Username updated successfully!");
    }

    @PutMapping("/users/settings/password")
    public VerificationResponse changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        User user = requireAuth(authentication);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return new VerificationResponse(true, "Password updated successfully.");
    }

    @DeleteMapping("/users/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            Authentication authentication
    ) {
        User user = requireAuth(authentication);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorrect password");
        }

        userRepository.delete(user);
    }

    private User requireAuth(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    // ──────────────── API Key Management (Local vs Cloud Informed Choice) ────────────────

    @GetMapping("/users/api-key")
    public UserApiKeyStatusResponse getApiKeyStatus(Authentication authentication) {
        User user = requireAuth(authentication);
        boolean hasCloudKey = user.getEncryptedYoutubeApiKey() != null && !user.getEncryptedYoutubeApiKey().isBlank();
        String maskedKey = null;
        if (hasCloudKey) {
            String decrypted = encryptionService.decrypt(user.getEncryptedYoutubeApiKey());
            maskedKey = encryptionService.maskKey(decrypted);
        }
        String pref = user.getApiKeyStoragePreference();
        if (pref == null || pref.isBlank()) {
            pref = hasCloudKey ? "CLOUD" : "NONE";
        }
        return new UserApiKeyStatusResponse(hasCloudKey, pref, maskedKey);
    }

    @PutMapping("/users/api-key")
    public UserApiKeyStatusResponse updateApiKey(
            @Valid @RequestBody UpdateApiKeyRequest request,
            Authentication authentication
    ) {
        User user = requireAuth(authentication);
        String pref = request.storagePreference() != null ? request.storagePreference().trim().toUpperCase() : "NONE";

        if ("CLOUD".equals(pref)) {
            String rawKey = request.apiKey() != null ? request.apiKey().trim() : "";
            if (rawKey.isEmpty()) {
                user.setEncryptedYoutubeApiKey(null);
                user.setApiKeyStoragePreference("NONE");
                evictUserKeyCache(user.getUsername());
            } else {
                if (!rawKey.startsWith("AIzaSy")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google Cloud API keys must begin with 'AIzaSy'");
                }
                String encrypted = encryptionService.encrypt(rawKey);
                user.setEncryptedYoutubeApiKey(encrypted);
                user.setApiKeyStoragePreference("CLOUD");
                cacheUserKey(user.getUsername(), rawKey);
            }
        } else if ("LOCAL".equals(pref)) {
            // User explicitly chose local storage: clear cloud-stored DB key and cache
            user.setEncryptedYoutubeApiKey(null);
            user.setApiKeyStoragePreference("LOCAL");
            evictUserKeyCache(user.getUsername());
        } else {
            user.setEncryptedYoutubeApiKey(null);
            user.setApiKeyStoragePreference("NONE");
            evictUserKeyCache(user.getUsername());
        }

        userRepository.save(user);

        boolean hasCloudKey = user.getEncryptedYoutubeApiKey() != null && !user.getEncryptedYoutubeApiKey().isBlank();
        String masked = null;
        if (hasCloudKey) {
            String dec = encryptionService.decrypt(user.getEncryptedYoutubeApiKey());
            masked = encryptionService.maskKey(dec);
        }
        return new UserApiKeyStatusResponse(hasCloudKey, user.getApiKeyStoragePreference(), masked);
    }

    @DeleteMapping("/users/api-key")
    public UserApiKeyStatusResponse deleteApiKey(Authentication authentication) {
        User user = requireAuth(authentication);
        user.setEncryptedYoutubeApiKey(null);
        user.setApiKeyStoragePreference("NONE");
        evictUserKeyCache(user.getUsername());
        userRepository.save(user);
        return new UserApiKeyStatusResponse(false, "NONE", null);
    }

    @GetMapping("/users/internal/{username}/youtube-api-key")
    public InternalApiKeyResponse getInternalApiKey(@PathVariable String username) {
        if (username == null || username.isBlank()) {
            return new InternalApiKeyResponse(username, null);
        }
        return userRepository.findByUsername(username)
                .map(user -> {
                    String enc = user.getEncryptedYoutubeApiKey();
                    if (enc != null && !enc.isBlank()) {
                        String dec = encryptionService.decrypt(enc);
                        return new InternalApiKeyResponse(username, dec);
                    }
                    return new InternalApiKeyResponse(username, null);
                })
                .orElse(new InternalApiKeyResponse(username, null));
    }

    private void cacheUserKey(String username, String rawKey) {
        if (stringRedisTemplate != null && username != null && rawKey != null) {
            try {
                stringRedisTemplate.opsForValue().set("aficio:user_key:" + username, rawKey, java.time.Duration.ofDays(30));
            } catch (Exception ex) {
                log.warn("Failed to cache decrypted user API key in Redis: {}", ex.getMessage());
            }
        }
    }

    private void evictUserKeyCache(String username) {
        if (stringRedisTemplate != null && username != null) {
            try {
                stringRedisTemplate.delete("aficio:user_key:" + username);
            } catch (Exception ex) {
                log.warn("Failed to evict user API key from Redis: {}", ex.getMessage());
            }
        }
    }

    public record RegistrationRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
            String username,
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,
            @NotBlank(message = "Password is required")
            @Size(min = 6, message = "Password must be at least 6 characters")
            String password
    ) {
    }

    public record RegistrationResponse(
            Long id,
            String username,
            String email,
            String verificationUrl
    ) {
    }

    public record LoginRequest(
            String username,
            String email,
            String usernameOrEmail,
            @NotBlank(message = "Password is required")
            String password
    ) {
        public String getIdentifier() {
            if (usernameOrEmail != null && !usernameOrEmail.isBlank()) return usernameOrEmail.trim();
            if (email != null && !email.isBlank()) return email.trim();
            if (username != null && !username.isBlank()) return username.trim();
            return "";
        }
    }

    public record LoginResponse(
            String token,
            boolean requires2fa
    ) {
    }

    public record GoogleOAuthRequest(
            @NotBlank(message = "Google ID token is required")
            String idToken
    ) {
    }

    public record VerificationResponse(
            boolean success,
            String message
    ) {
    }

    public record UserProfileResponse(
            Long id,
            String username,
            String displayName,
            String bio,
            String avatarUrl,
            boolean isPrivate
    ) {
    }

    public record MeResponse(
            Long id,
            String username,
            String email,
            String displayName,
            String bio,
            String avatarUrl,
            boolean isPrivate,
            boolean twoFactorEnabled
    ) {
    }

    public record SettingsResponse(
            String username,
            String email,
            String displayName,
            String bio,
            String avatarUrl,
            boolean isPrivate,
            boolean twoFactorEnabled
    ) {
    }

    public record UpdateSettingsRequest(
            @Size(max = 100, message = "Display name must be 100 characters or less")
            String displayName,
            @Size(max = 500, message = "Bio must be 500 characters or less")
            String bio,
            @Size(max = 512, message = "Avatar URL must be 512 characters or less")
            String avatarUrl,
            Boolean isPrivate
    ) {
    }

    public record UpdateUsernameRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
            String username
    ) {
    }

    public record UsernameUpdateResponse(
            String username,
            String token,
            String message
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required")
            String currentPassword,
            @NotBlank(message = "New password is required")
            @Size(min = 6, message = "New password must be at least 6 characters")
            String newPassword
    ) {
    }

    public record DeleteAccountRequest(
            @NotBlank(message = "Password is required")
            String password
    ) {
    }

    public record PrivacyResponse(
            boolean isPrivate
    ) {
    }

    public record TwoFactorVerifyRequest(
            @NotBlank(message = "Code is required")
            String code
    ) {
    }

    public record ResendVerificationRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email
    ) {
    }

    public record TwoFactorSetupResponse(
            String secret,
            String qrCodeUri
    ) {
    }

    public record UserApiKeyStatusResponse(
            boolean hasCloudKey,
            String storagePreference,
            String maskedKey
    ) {
    }

    public record UpdateApiKeyRequest(
            String apiKey,
            @NotBlank(message = "Storage preference is required")
            String storagePreference
    ) {
    }

    public record InternalApiKeyResponse(
            String username,
            String apiKey
    ) {
    }
}
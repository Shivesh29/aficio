package com.aficio.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Provides industry-standard authenticated encryption at rest using AES-256-GCM
 * (Galois/Counter Mode with 128-bit authentication tags and unique 12-byte IVs).
 */
@Service
public class AesGcmEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(AesGcmEncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptionService(
            @Value("${aficio.security.api-key-encryption-secret:AficioAesGcm256EncryptionMasterKey2026!}") String secret
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available for key derivation", e);
        }
    }

    /**
     * Encrypts plaintext into a Base64-encoded string containing [IV (12 bytes) + Ciphertext + AuthTag (16 bytes)].
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherTextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherTextWithTag.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherTextWithTag);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt sensitive data with AES-256-GCM: {}", e.getMessage());
            throw new RuntimeException("Encryption failure", e);
        }
    }

    /**
     * Decrypts a Base64-encoded [IV + Ciphertext + AuthTag] back to the original plaintext.
     */
    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encryptedBase64);
            if (decoded.length < GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
                throw new IllegalArgumentException("Encrypted payload too short to be valid AES-GCM");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] cipherTextWithTag = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherTextWithTag);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decrypted = cipher.doFinal(cipherTextWithTag);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt AES-256-GCM payload: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Masks an API key for safe display in UI or logs (e.g. AIzaSy••••••••••••••••••••••••••••••••3aB8).
     */
    public String maskKey(String key) {
        if (key == null || key.isBlank()) return null;
        String trimmed = key.trim();
        if (trimmed.length() <= 10) {
            return "AIzaSy...";
        }
        String prefix = trimmed.substring(0, Math.min(6, trimmed.length()));
        String suffix = trimmed.substring(trimmed.length() - 4);
        return prefix + "••••••••••••••••••••••••••••••••" + suffix;
    }
}

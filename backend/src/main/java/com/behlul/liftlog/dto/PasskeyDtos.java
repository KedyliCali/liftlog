package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.Passkey;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response records for passkeys. The WebAuthn parts (publicKey, credential) stay raw JSON: the
 * Yubico library reads and writes them itself, so our own JSON mapper never has to understand its types.
 */
public final class PasskeyDtos {

    /** base64url of 32 random bytes without padding: what the phone generates, and nothing else. */
    public static final String DEVICE_SECRET_PATTERN = "[A-Za-z0-9_-]{43}";

    private PasskeyDtos() {
    }

    /**
     * @param expiresAt when the server forgets this ceremony (UTC); finishing later gets 400
     * @param publicKey exactly what navigator.credentials.create/get needs, binary values as base64url
     */
    public record PasskeyOptionsResponse(UUID ceremonyId, Instant expiresAt, JsonNode publicKey) {
    }

    /** @param credential the PublicKeyCredential the browser returned, serialized to JSON (base64url binaries) */
    public record RegisterPasskeyRequest(@NotNull UUID ceremonyId, @NotNull JsonNode credential) {
    }

    public record PasskeyCreatedResponse(Long id, LocalDateTime createdAt) {
        public static PasskeyCreatedResponse from(Passkey passkey) {
            return new PasskeyCreatedResponse(passkey.getId(), passkey.getCreatedAt());
        }
    }

    public record PasskeyResponse(Long id, LocalDateTime createdAt, LocalDateTime lastUsedAt) {
        public static PasskeyResponse from(Passkey passkey) {
            return new PasskeyResponse(passkey.getId(), passkey.getCreatedAt(), passkey.getLastUsedAt());
        }
    }

    /**
     * @param deviceSecret a fresh random secret the phone made (base64url of 32 bytes: 43 characters). It
     *                     becomes an extra password of the account, so the device signs in silently next time.
     *                     The exact shape also keeps it far below BCrypt's 72-byte limit.
     */
    public record PasskeyLoginRequest(
            @NotNull UUID ceremonyId,
            @NotNull JsonNode credential,
            @NotNull @Pattern(regexp = DEVICE_SECRET_PATTERN) String deviceSecret
    ) {
        // The device secret is a password; it must never end up in a log line
        @Override
        public String toString() {
            return "PasskeyLoginRequest[ceremonyId=" + ceremonyId + ", deviceSecret=[redacted]]";
        }
    }
}

package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.Passkey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request/response records for passkeys. The WebAuthn parts (publicKey, credential) stay raw JSON: the
 * Yubico library reads and writes them itself, so our own JSON mapper never has to understand its types.
 */
public final class PasskeyDtos {

    private PasskeyDtos() {
    }

    /** @param publicKey exactly what navigator.credentials.create/get needs, binary values as base64url */
    public record PasskeyOptionsResponse(UUID ceremonyId, JsonNode publicKey) {
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
     *                     At most 72 characters because BCrypt only reads the first 72 bytes (and Spring's
     *                     encoder refuses longer input).
     */
    public record PasskeyLoginRequest(
            @NotNull UUID ceremonyId,
            @NotNull JsonNode credential,
            @NotBlank @Size(min = 32, max = 72) String deviceSecret
    ) {
        // The device secret is a password; it must never end up in a log line
        @Override
        public String toString() {
            return "PasskeyLoginRequest[ceremonyId=" + ceremonyId + ", deviceSecret=[redacted]]";
        }
    }
}

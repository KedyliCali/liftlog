package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request/response records for /api/v1/auth, grouped because they only make sense together. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            // BCrypt only uses the first 72 bytes, so longer passwords would be silently truncated
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 100) String displayName
    ) {
        // Records print every field; the password must never end up in a log line
        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + ", password=[redacted]]";
        }
    }

    /** The web app's recovery code is this email and password, so it is just as secret. */
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=[redacted]]";
        }
    }

    /** The web app sends both automatically when the user asks for a new recovery code. */
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            // Same rule as RegisterRequest: BCrypt only reads the first 72 bytes
            @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[redacted]";
        }
    }

    public record UserResponse(Long id, String email, String displayName) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
        }
    }

    public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
    }
}

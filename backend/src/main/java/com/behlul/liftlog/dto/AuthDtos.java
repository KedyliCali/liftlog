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
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserResponse(Long id, String email, String displayName) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
        }
    }

    public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
    }
}

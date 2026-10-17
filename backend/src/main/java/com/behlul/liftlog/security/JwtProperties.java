package com.behlul.liftlog.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * @param secret HMAC key for signing tokens; must be at least 32 bytes for HS256
 * @param ttl    token lifetime; long-lived because the mobile app has no refresh-token flow yet
 */
@ConfigurationProperties("liftlog.jwt")
public record JwtProperties(String secret, Duration ttl) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("liftlog.jwt.secret (JWT_SECRET) must be at least 32 bytes");
        }
        if (ttl == null) {
            ttl = Duration.ofDays(30);
        }
    }
}

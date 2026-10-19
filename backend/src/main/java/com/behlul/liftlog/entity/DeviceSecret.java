package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * An extra password a device received after a passkey sign-in, so it can sign in silently afterwards.
 * Stored as a BCrypt hash, exactly like the main password.
 */
@Entity
@Table(name = "device_secrets")
public class DeviceSecret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "secret_hash", nullable = false, length = 100)
    private String secretHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Decides eviction: the least recently used secret goes first (see AuthService.MAX_DEVICE_SECRETS). */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    protected DeviceSecret() {
    }

    public DeviceSecret(Long userId, String secretHash, LocalDateTime createdAt) {
        this.userId = userId;
        this.secretHash = secretHash;
        this.createdAt = createdAt;
        // Getting the secret is its first use, so a brand-new device is not the first one evicted
        this.lastUsedAt = createdAt;
    }

    public void markUsed(LocalDateTime now) {
        this.lastUsedAt = now;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

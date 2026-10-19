package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A started passkey registration or sign-in: the options JSON (with its challenge) handed to the browser.
 * Single use and short-lived; see PasskeyCeremonyStore.
 */
@Entity
@Table(name = "webauthn_ceremonies")
public class WebAuthnCeremony {

    public enum Kind { REGISTER, LOGIN }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    /** The signed-in user for REGISTER; null for LOGIN, where nobody is known yet. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "request_json", nullable = false, columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected WebAuthnCeremony() {
    }

    public WebAuthnCeremony(UUID id, Kind kind, Long userId, String requestJson, LocalDateTime expiresAt) {
        this.id = id;
        this.kind = kind;
        this.userId = userId;
        this.requestJson = requestJson;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}

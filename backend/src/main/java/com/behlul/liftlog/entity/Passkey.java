package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** A registered WebAuthn credential. Only the public key is stored; the private key never leaves the phone. */
@Entity
@Table(name = "passkeys")
public class Passkey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** base64url, the form WebAuthn JSON uses, so lookups need no conversion. */
    @Column(name = "credential_id", nullable = false, unique = true, length = 1024)
    private String credentialId;

    @Column(name = "public_key_cose", nullable = false)
    private byte[] publicKeyCose;

    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    protected Passkey() {
    }

    public Passkey(Long userId, String credentialId, byte[] publicKeyCose, long signatureCount, LocalDateTime createdAt) {
        this.userId = userId;
        this.credentialId = credentialId;
        this.publicKeyCose = publicKeyCose;
        this.signatureCount = signatureCount;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public byte[] getPublicKeyCose() {
        return publicKeyCose;
    }

    public long getSignatureCount() {
        return signatureCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }
}

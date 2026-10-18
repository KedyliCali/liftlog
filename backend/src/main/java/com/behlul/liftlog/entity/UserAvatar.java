package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** A user's profile photo. Kept out of {@link User} so the bytes load only when the image is requested. */
@Entity
@Table(name = "user_avatars")
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    // byte[] maps to PostgreSQL BYTEA; @Lob would use large-object OIDs instead
    @Column(nullable = false)
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserAvatar() {
    }

    public UserAvatar(Long userId) {
        this.userId = userId;
    }

    public void replace(String contentType, byte[] data, LocalDateTime now) {
        this.contentType = contentType;
        this.data = data;
        this.updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

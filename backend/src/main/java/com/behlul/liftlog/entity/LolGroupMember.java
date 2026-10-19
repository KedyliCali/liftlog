package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * A user's membership in their (single) League group, plus the Riot account they play on.
 *
 * <p>The id is the user's own id, assigned by us. Without {@link Persistable}, save() would merge, and a join
 * racing another join could silently move the user into the other group instead of failing on the primary key.
 */
@Entity
@Table(name = "lol_group_members")
public class LolGroupMember implements Persistable<Long> {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** A plain id rather than a relation: the service always works with one group at a time. */
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    private String puuid;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Transient
    private boolean isNew = true;

    protected LolGroupMember() {
    }

    public LolGroupMember(Long userId, Long groupId, LocalDateTime joinedAt) {
        this.userId = userId;
        this.groupId = groupId;
        this.joinedAt = joinedAt;
    }

    public void linkAccount(String puuid) {
        this.puuid = puuid;
    }

    @Override
    public Long getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        isNew = false;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getPuuid() {
        return puuid;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}

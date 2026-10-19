package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;

/** One participant's totals at one minute of a match, from Riot's timeline. Written once, never changed. */
@Entity
@Table(name = "lol_match_frames")
@IdClass(LolMatchFrame.Key.class)
public class LolMatchFrame implements Persistable<LolMatchFrame.Key> {

    @Id
    @Column(name = "match_id")
    private String matchId;

    @Id
    private int minute;

    @Id
    @Column(name = "participant_id")
    private int participantId;

    private int gold;
    private int xp;
    /** Lane minions plus jungle monsters. */
    private int cs;
    private int level;

    @Transient
    private boolean isNew = true;

    protected LolMatchFrame() {
    }

    public LolMatchFrame(String matchId, int minute, int participantId, int gold, int xp, int cs, int level) {
        this.matchId = matchId;
        this.minute = minute;
        this.participantId = participantId;
        this.gold = gold;
        this.xp = xp;
        this.cs = cs;
        this.level = level;
    }

    public record Key(String matchId, int minute, int participantId) implements Serializable {
        // JPA needs a no-argument constructor for id classes
        public Key() {
            this(null, 0, 0);
        }
    }

    @Override
    public Key getId() {
        return new Key(matchId, minute, participantId);
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

    public String getMatchId() {
        return matchId;
    }

    public int getMinute() {
        return minute;
    }

    public int getParticipantId() {
        return participantId;
    }

    public int getGold() {
        return gold;
    }

    public int getXp() {
        return xp;
    }

    public int getCs() {
        return cs;
    }

    public int getLevel() {
        return level;
    }
}

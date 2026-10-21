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
import java.time.LocalDateTime;

/** One champion's mastery for a player; the top ones are replaced as a whole on each mastery fetch. */
@Entity
@Table(name = "lol_champion_mastery")
@IdClass(LolChampionMastery.Key.class)
public class LolChampionMastery implements Persistable<LolChampionMastery.Key> {

    @Id
    private String puuid;

    /** Data Dragon's numeric champion key. */
    @Id
    @Column(name = "champion_id")
    private int championId;

    @Column(name = "champion_level", nullable = false)
    private int championLevel;

    @Column(name = "champion_points", nullable = false)
    private long championPoints;

    @Column(name = "last_play_time")
    private LocalDateTime lastPlayTime;

    // Rows are always new when saved (the old ones are deleted first), so saving inserts without a select first
    @Transient
    private boolean isNew = true;

    protected LolChampionMastery() {
    }

    public LolChampionMastery(String puuid, int championId, int championLevel, long championPoints,
                              LocalDateTime lastPlayTime) {
        this.puuid = puuid;
        this.championId = championId;
        this.championLevel = championLevel;
        this.championPoints = championPoints;
        this.lastPlayTime = lastPlayTime;
    }

    public record Key(String puuid, int championId) implements Serializable {
        // JPA needs a no-argument constructor for id classes
        public Key() {
            this(null, 0);
        }
    }

    @Override
    public Key getId() {
        return new Key(puuid, championId);
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

    public String getPuuid() {
        return puuid;
    }

    public int getChampionId() {
        return championId;
    }

    public int getChampionLevel() {
        return championLevel;
    }

    public long getChampionPoints() {
        return championPoints;
    }

    public LocalDateTime getLastPlayTime() {
        return lastPlayTime;
    }
}

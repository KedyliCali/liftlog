package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

/** A player's solo/duo rank at one moment; a new row only when the rank or LP moved. */
@Entity
@Table(name = "lol_rank_history")
@IdClass(LolRankHistory.Key.class)
public class LolRankHistory {

    @Id
    private String puuid;

    @Id
    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false)
    private String division;

    private int lp;
    private int wins;
    private int losses;

    protected LolRankHistory() {
    }

    public LolRankHistory(String puuid, LocalDateTime recordedAt, String tier, String division, int lp, int wins,
                          int losses) {
        this.puuid = puuid;
        this.recordedAt = recordedAt;
        this.tier = tier;
        this.division = division;
        this.lp = lp;
        this.wins = wins;
        this.losses = losses;
    }

    public record Key(String puuid, LocalDateTime recordedAt) implements Serializable {
        // JPA needs a no-argument constructor for id classes
        public Key() {
            this(null, null);
        }
    }

    /** Wins and losses change every game, so only these three decide whether a new point is worth storing. */
    public boolean sameRank(String otherTier, String otherDivision, int otherLp) {
        return tier.equals(otherTier) && division.equals(otherDivision) && lp == otherLp;
    }

    public String getPuuid() {
        return puuid;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public String getTier() {
        return tier;
    }

    public String getDivision() {
        return division;
    }

    public int getLp() {
        return lp;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }
}

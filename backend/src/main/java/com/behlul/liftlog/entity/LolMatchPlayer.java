package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

/** One followed player's line from one match. */
@Entity
@Table(name = "lol_match_players")
@IdClass(LolMatchPlayer.Key.class)
public class LolMatchPlayer {

    @Id
    @Column(name = "match_id")
    private String matchId;

    @Id
    private String puuid;

    @Column(name = "queue_id", nullable = false)
    private int queueId;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    @Column(name = "duration_s", nullable = false)
    private int durationS;

    @Column(nullable = false)
    private String champion;

    private String position;

    private int kills;
    private int deaths;
    private int assists;
    private int cs;
    private int damage;
    private boolean win;

    protected LolMatchPlayer() {
    }

    public LolMatchPlayer(String matchId, String puuid, int queueId, LocalDateTime endedAt, int durationS, String champion,
                          String position, int kills, int deaths, int assists, int cs, int damage, boolean win) {
        this.matchId = matchId;
        this.puuid = puuid;
        this.queueId = queueId;
        this.endedAt = endedAt;
        this.durationS = durationS;
        this.champion = champion;
        this.position = position;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.cs = cs;
        this.damage = damage;
        this.win = win;
    }

    public record Key(String matchId, String puuid) implements Serializable {
        // JPA needs a no-argument constructor for id classes
        public Key() {
            this(null, null);
        }
    }

    public String getMatchId() {
        return matchId;
    }

    public String getPuuid() {
        return puuid;
    }

    public int getQueueId() {
        return queueId;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public int getDurationS() {
        return durationS;
    }

    public String getChampion() {
        return champion;
    }

    public String getPosition() {
        return position;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getAssists() {
        return assists;
    }

    public int getCs() {
        return cs;
    }

    public int getDamage() {
        return damage;
    }

    public boolean isWin() {
        return win;
    }
}

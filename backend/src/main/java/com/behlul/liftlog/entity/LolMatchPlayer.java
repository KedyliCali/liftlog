package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
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

    // The detail columns below are null on rows stored before they existed, until a refresh re-downloads them

    @Column(name = "gold_earned")
    private Integer goldEarned;

    @Column(name = "vision_score")
    private Integer visionScore;

    @Column(name = "champ_level")
    private Integer champLevel;

    @Column(name = "damage_taken")
    private Integer damageTaken;

    @Column(name = "kill_participation")
    private BigDecimal killParticipation;

    @Column(name = "damage_share")
    private BigDecimal damageShare;

    @Column(name = "largest_multi_kill")
    private Integer largestMultiKill;

    @Column(name = "wards_placed")
    private Integer wardsPlaced;

    /** Seven item ids, comma separated, 0 for an empty slot. */
    private String items;

    /** Two summoner spell ids, comma separated. */
    @Column(name = "summoner_spells")
    private String summonerSpells;

    @Column(name = "game_mode")
    private String gameMode;

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

    /**
     * The numbers added after the first version. Kept apart from the constructor so a stored row can be filled in
     * later (the backfill) with the same code that fills a new one.
     *
     * @param killParticipation 0..1, null when Riot did not send it (some modes)
     * @param damageShare       share of the team's champion damage, 0..1, null when Riot did not send it
     */
    public record Details(Integer goldEarned, Integer visionScore, Integer champLevel, Integer damageTaken,
                          BigDecimal killParticipation, BigDecimal damageShare, Integer largestMultiKill,
                          Integer wardsPlaced, String items, String summonerSpells, String gameMode) {
    }

    public void fillDetails(Details details) {
        this.goldEarned = details.goldEarned();
        this.visionScore = details.visionScore();
        this.champLevel = details.champLevel();
        this.damageTaken = details.damageTaken();
        this.killParticipation = details.killParticipation();
        this.damageShare = details.damageShare();
        this.largestMultiKill = details.largestMultiKill();
        this.wardsPlaced = details.wardsPlaced();
        this.items = details.items();
        this.summonerSpells = details.summonerSpells();
        this.gameMode = details.gameMode();
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

    public Integer getGoldEarned() {
        return goldEarned;
    }

    public Integer getVisionScore() {
        return visionScore;
    }

    public Integer getChampLevel() {
        return champLevel;
    }

    public Integer getDamageTaken() {
        return damageTaken;
    }

    public BigDecimal getKillParticipation() {
        return killParticipation;
    }

    public BigDecimal getDamageShare() {
        return damageShare;
    }

    public Integer getLargestMultiKill() {
        return largestMultiKill;
    }

    public Integer getWardsPlaced() {
        return wardsPlaced;
    }

    public String getItems() {
        return items;
    }

    public String getSummonerSpells() {
        return summonerSpells;
    }

    public String getGameMode() {
        return gameMode;
    }
}

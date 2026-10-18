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
 * Cached copy of a League player's public profile and solo/duo rank.
 *
 * <p>The id (PUUID) comes from Riot rather than the database, so Spring Data cannot tell a new player from a
 * stored one by a null id; {@link Persistable} tells it, so a new player is inserted instead of merged.
 */
@Entity
@Table(name = "lol_players")
public class LolPlayer implements Persistable<String> {

    @Id
    private String puuid;

    @Column(nullable = false)
    private String platform;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "tag_line", nullable = false)
    private String tagLine;

    @Column(name = "profile_icon_id")
    private Integer profileIconId;

    @Column(name = "summoner_level")
    private Integer summonerLevel;

    @Column(name = "solo_tier")
    private String soloTier;

    @Column(name = "solo_division")
    private String soloDivision;

    @Column(name = "solo_lp")
    private Integer soloLp;

    @Column(name = "solo_wins")
    private Integer soloWins;

    @Column(name = "solo_losses")
    private Integer soloLosses;

    @Column(name = "refreshed_at")
    private LocalDateTime refreshedAt;

    @Transient
    private boolean isNew = true;

    protected LolPlayer() {
    }

    public LolPlayer(String puuid, String platform, String gameName, String tagLine) {
        this.puuid = puuid;
        this.platform = platform;
        this.gameName = gameName;
        this.tagLine = tagLine;
    }

    /** Riot IDs can be renamed; the PUUID stays, so names are refreshed from every account lookup. */
    public void rename(String gameName, String tagLine) {
        this.gameName = gameName;
        this.tagLine = tagLine;
    }

    public void updateProfile(int profileIconId, long summonerLevel) {
        this.profileIconId = profileIconId;
        this.summonerLevel = (int) summonerLevel;
    }

    public void updateSoloRank(String tier, String division, Integer lp, Integer wins, Integer losses) {
        this.soloTier = tier;
        this.soloDivision = division;
        this.soloLp = lp;
        this.soloWins = wins;
        this.soloLosses = losses;
    }

    public void markRefreshed(LocalDateTime at) {
        this.refreshedAt = at;
    }

    @Override
    public String getId() {
        return puuid;
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

    public String getPlatform() {
        return platform;
    }

    public String getGameName() {
        return gameName;
    }

    public String getTagLine() {
        return tagLine;
    }

    public Integer getProfileIconId() {
        return profileIconId;
    }

    public Integer getSummonerLevel() {
        return summonerLevel;
    }

    public String getSoloTier() {
        return soloTier;
    }

    public String getSoloDivision() {
        return soloDivision;
    }

    public Integer getSoloLp() {
        return soloLp;
    }

    public Integer getSoloWins() {
        return soloWins;
    }

    public Integer getSoloLosses() {
        return soloLosses;
    }

    public LocalDateTime getRefreshedAt() {
        return refreshedAt;
    }
}

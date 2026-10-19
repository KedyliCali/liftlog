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
import java.math.BigDecimal;

/**
 * One of the (usually ten) players of a stored match, followed or not: what duos, lane matchups and the full
 * scoreboard are built from. Written once, never changed.
 */
@Entity
@Table(name = "lol_match_participants")
@IdClass(LolMatchParticipant.Key.class)
public class LolMatchParticipant implements Persistable<LolMatchParticipant.Key> {

    @Id
    @Column(name = "match_id")
    private String matchId;

    /** Riot's 1..10; the timeline's frames use the same ids. */
    @Id
    @Column(name = "participant_id")
    private int participantId;

    @Column(nullable = false)
    private String puuid;

    @Column(name = "riot_name")
    private String riotName;

    @Column(name = "riot_tag")
    private String riotTag;

    @Column(name = "team_id")
    private int teamId;

    @Column(nullable = false)
    private String champion;

    @Column(name = "champ_level")
    private int champLevel;

    /** Null in modes without lanes (ARAM). */
    private String position;

    private int kills;
    private int deaths;
    private int assists;
    private int cs;
    private int gold;
    /** To champions. */
    private int damage;

    @Column(name = "damage_taken")
    private int damageTaken;

    @Column(name = "damage_objectives")
    private int damageObjectives;

    @Column(name = "damage_buildings")
    private int damageBuildings;

    private int healing;
    /** Shields on teammates. */
    private int shielding;

    /** Seconds of crowd control on enemies. */
    @Column(name = "cc_time")
    private int ccTime;

    @Column(name = "time_dead")
    private int timeDead;

    @Column(name = "vision_score")
    private int visionScore;

    @Column(name = "wards_placed")
    private int wardsPlaced;

    @Column(name = "wards_killed")
    private int wardsKilled;

    @Column(name = "control_wards")
    private int controlWards;

    @Column(name = "double_kills")
    private int doubleKills;

    @Column(name = "triple_kills")
    private int tripleKills;

    @Column(name = "quadra_kills")
    private int quadraKills;

    @Column(name = "penta_kills")
    private int pentaKills;

    /** Took part in the first kill of the game, as the killer or with an assist. */
    @Column(name = "first_blood")
    private boolean firstBlood;

    /** Null when Riot did not send it (some modes), unlike the counters above, which read a missing value as 0. */
    @Column(name = "solo_kills")
    private Integer soloKills;

    @Column(name = "kill_participation")
    private BigDecimal killParticipation;

    @Column(name = "damage_share")
    private BigDecimal damageShare;

    /** Seven item ids, comma separated, 0 for an empty slot; the last one is the trinket. */
    private String items;

    @Column(name = "summoner_spells")
    private String summonerSpells;

    private Integer keystone;

    @Column(name = "primary_style")
    private Integer primaryStyle;

    @Column(name = "secondary_style")
    private Integer secondaryStyle;

    private boolean win;

    @Transient
    private boolean isNew = true;

    protected LolMatchParticipant() {
    }

    /** The scoreboard numbers that are always there; the rest is filled with {@link #fill}. */
    public LolMatchParticipant(String matchId, int participantId, String puuid, String riotName, String riotTag,
                               int teamId, String champion, int champLevel, String position, int kills, int deaths,
                               int assists, int cs, int gold, int damage, boolean win) {
        this.matchId = matchId;
        this.participantId = participantId;
        this.puuid = puuid;
        this.riotName = riotName;
        this.riotTag = riotTag;
        this.teamId = teamId;
        this.champion = champion;
        this.champLevel = champLevel;
        this.position = position;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.cs = cs;
        this.gold = gold;
        this.damage = damage;
        this.win = win;
    }

    /** The less central numbers, kept apart so the constructor stays readable. Missing counters are 0. */
    public record Extra(int damageTaken, int damageObjectives, int damageBuildings, int healing, int shielding,
                        int ccTime, int timeDead, int visionScore, int wardsPlaced, int wardsKilled, int controlWards,
                        int doubleKills, int tripleKills, int quadraKills, int pentaKills, boolean firstBlood,
                        Integer soloKills, BigDecimal killParticipation, BigDecimal damageShare, String items,
                        String summonerSpells, Integer keystone, Integer primaryStyle, Integer secondaryStyle) {
    }

    public LolMatchParticipant fill(Extra extra) {
        this.damageTaken = extra.damageTaken();
        this.damageObjectives = extra.damageObjectives();
        this.damageBuildings = extra.damageBuildings();
        this.healing = extra.healing();
        this.shielding = extra.shielding();
        this.ccTime = extra.ccTime();
        this.timeDead = extra.timeDead();
        this.visionScore = extra.visionScore();
        this.wardsPlaced = extra.wardsPlaced();
        this.wardsKilled = extra.wardsKilled();
        this.controlWards = extra.controlWards();
        this.doubleKills = extra.doubleKills();
        this.tripleKills = extra.tripleKills();
        this.quadraKills = extra.quadraKills();
        this.pentaKills = extra.pentaKills();
        this.firstBlood = extra.firstBlood();
        this.soloKills = extra.soloKills();
        this.killParticipation = extra.killParticipation();
        this.damageShare = extra.damageShare();
        this.items = extra.items();
        this.summonerSpells = extra.summonerSpells();
        this.keystone = extra.keystone();
        this.primaryStyle = extra.primaryStyle();
        this.secondaryStyle = extra.secondaryStyle();
        return this;
    }

    public record Key(String matchId, int participantId) implements Serializable {
        // JPA needs a no-argument constructor for id classes
        public Key() {
            this(null, 0);
        }
    }

    @Override
    public Key getId() {
        return new Key(matchId, participantId);
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

    public int getParticipantId() {
        return participantId;
    }

    public String getPuuid() {
        return puuid;
    }

    public String getRiotName() {
        return riotName;
    }

    public String getRiotTag() {
        return riotTag;
    }

    public int getTeamId() {
        return teamId;
    }

    public String getChampion() {
        return champion;
    }

    public int getChampLevel() {
        return champLevel;
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

    public int getGold() {
        return gold;
    }

    public int getDamage() {
        return damage;
    }

    public int getDamageTaken() {
        return damageTaken;
    }

    public int getDamageObjectives() {
        return damageObjectives;
    }

    public int getDamageBuildings() {
        return damageBuildings;
    }

    public int getHealing() {
        return healing;
    }

    public int getShielding() {
        return shielding;
    }

    public int getCcTime() {
        return ccTime;
    }

    public int getTimeDead() {
        return timeDead;
    }

    public int getVisionScore() {
        return visionScore;
    }

    public int getWardsPlaced() {
        return wardsPlaced;
    }

    public int getWardsKilled() {
        return wardsKilled;
    }

    public int getControlWards() {
        return controlWards;
    }

    public int getDoubleKills() {
        return doubleKills;
    }

    public int getTripleKills() {
        return tripleKills;
    }

    public int getQuadraKills() {
        return quadraKills;
    }

    public int getPentaKills() {
        return pentaKills;
    }

    public boolean isFirstBlood() {
        return firstBlood;
    }

    public Integer getSoloKills() {
        return soloKills;
    }

    public BigDecimal getKillParticipation() {
        return killParticipation;
    }

    public BigDecimal getDamageShare() {
        return damageShare;
    }

    public String getItems() {
        return items;
    }

    public String getSummonerSpells() {
        return summonerSpells;
    }

    public Integer getKeystone() {
        return keystone;
    }

    public Integer getPrimaryStyle() {
        return primaryStyle;
    }

    public Integer getSecondaryStyle() {
        return secondaryStyle;
    }

    public boolean isWin() {
        return win;
    }
}

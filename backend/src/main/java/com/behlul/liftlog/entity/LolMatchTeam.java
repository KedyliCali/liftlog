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

/**
 * One side of a stored match: result, objectives and bans. Written once, never changed; {@link Persistable}
 * makes save() insert directly instead of first selecting the row to merge into.
 */
@Entity
@Table(name = "lol_match_teams")
@IdClass(LolMatchTeam.Key.class)
public class LolMatchTeam implements Persistable<LolMatchTeam.Key> {

    @Id
    @Column(name = "match_id")
    private String matchId;

    /** 100 blue, 200 red. */
    @Id
    @Column(name = "team_id")
    private int teamId;

    private boolean win;
    private int kills;
    private int towers;
    private int inhibitors;
    private int dragons;
    private int barons;
    private int heralds;
    /** Void grubs (Riot's "horde"). */
    private int grubs;
    private int atakhan;

    @Column(name = "first_blood")
    private boolean firstBlood;

    @Column(name = "first_tower")
    private boolean firstTower;

    @Column(name = "first_dragon")
    private boolean firstDragon;

    @Column(name = "first_baron")
    private boolean firstBaron;

    /** Champion keys in pick order, comma separated, -1 for a skipped ban. */
    private String bans;

    @Transient
    private boolean isNew = true;

    protected LolMatchTeam() {
    }

    public LolMatchTeam(String matchId, int teamId, boolean win, int kills, int towers, int inhibitors, int dragons,
                        int barons, int heralds, int grubs, int atakhan, boolean firstBlood, boolean firstTower,
                        boolean firstDragon, boolean firstBaron, String bans) {
        this.matchId = matchId;
        this.teamId = teamId;
        this.win = win;
        this.kills = kills;
        this.towers = towers;
        this.inhibitors = inhibitors;
        this.dragons = dragons;
        this.barons = barons;
        this.heralds = heralds;
        this.grubs = grubs;
        this.atakhan = atakhan;
        this.firstBlood = firstBlood;
        this.firstTower = firstTower;
        this.firstDragon = firstDragon;
        this.firstBaron = firstBaron;
        this.bans = bans;
    }

    public record Key(String matchId, int teamId) implements Serializable {
        // JPA needs a no-argument constructor for id classes
        public Key() {
            this(null, 0);
        }
    }

    @Override
    public Key getId() {
        return new Key(matchId, teamId);
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

    public int getTeamId() {
        return teamId;
    }

    public boolean isWin() {
        return win;
    }

    public int getKills() {
        return kills;
    }

    public int getTowers() {
        return towers;
    }

    public int getInhibitors() {
        return inhibitors;
    }

    public int getDragons() {
        return dragons;
    }

    public int getBarons() {
        return barons;
    }

    public int getHeralds() {
        return heralds;
    }

    public int getGrubs() {
        return grubs;
    }

    public int getAtakhan() {
        return atakhan;
    }

    public boolean isFirstBlood() {
        return firstBlood;
    }

    public boolean isFirstTower() {
        return firstTower;
    }

    public boolean isFirstDragon() {
        return firstDragon;
    }

    public boolean isFirstBaron() {
        return firstBaron;
    }

    public String getBans() {
        return bans;
    }
}

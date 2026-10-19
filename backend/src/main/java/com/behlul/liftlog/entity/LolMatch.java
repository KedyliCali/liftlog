package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A whole match, shared by every followed player who was in it. Only read through JPA: it is written with
 * {@code insert ... on conflict do nothing} (see LolMatchRepository#insertIfAbsent), because two refreshes can
 * download the same match at once and the second one must not fail.
 */
@Entity
@Table(name = "lol_matches")
public class LolMatch {

    @Id
    @Column(name = "match_id")
    private String matchId;

    @Column(nullable = false)
    private String platform;

    @Column(name = "queue_id", nullable = false)
    private int queueId;

    @Column(name = "game_mode")
    private String gameMode;

    /** "14.20.123.4567"; the patch is its first two parts. */
    @Column(name = "game_version")
    private String gameVersion;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    @Column(name = "duration_s", nullable = false)
    private int durationS;

    /** Riot was asked for the timeline; with no frames stored, that means Riot no longer had it. */
    @Column(name = "timeline_fetched", nullable = false)
    private boolean timelineFetched;

    protected LolMatch() {
    }

    /** For tests and the pure stats code; stored rows come from the repository. */
    public LolMatch(String matchId, String platform, int queueId, String gameMode, String gameVersion,
                    LocalDateTime endedAt, int durationS) {
        this.matchId = matchId;
        this.platform = platform;
        this.queueId = queueId;
        this.gameMode = gameMode;
        this.gameVersion = gameVersion;
        this.endedAt = endedAt;
        this.durationS = durationS;
    }

    /** "14.20" from "14.20.123.4567"; null when Riot sent no version. */
    public String getPatch() {
        if (gameVersion == null || gameVersion.isBlank()) {
            return null;
        }
        String[] parts = gameVersion.split("\\.");
        return parts.length < 2 ? gameVersion : parts[0] + "." + parts[1];
    }

    public String getMatchId() {
        return matchId;
    }

    public String getPlatform() {
        return platform;
    }

    public int getQueueId() {
        return queueId;
    }

    public String getGameMode() {
        return gameMode;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public int getDurationS() {
        return durationS;
    }

    public boolean isTimelineFetched() {
        return timelineFetched;
    }
}

package com.behlul.liftlog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Responses built from whole stored matches: the detailed stats page, the match page and its timeline, and the
 * group's duos. Field names are the contract with the web app (docs/design/lol-deep-stats.md).
 *
 * <p>As in {@link LolDtos}: fractions are 0..1 with four decimals, other averages and per-minute numbers have two,
 * and a number is null when no game in the window has it (instead of a misleading zero).
 */
public final class LolMatchDtos {

    private LolMatchDtos() {
    }

    // ---- Detailed stats (GET /lol/players/{puuid}/stats) ----

    /**
     * @param queue             the filter asked for: ALL, SOLO, FLEX, ARAM or OTHER
     * @param games             the window asked for (20, 50 or 100)
     * @param gamesFound        games in the window, at most {@code games}
     * @param gamesWithFullData of those, the ones stored whole; duos, matchups, sides, objectives, keystones and
     *                          patches are computed over these only
     * @param trend             one point per game, oldest first
     */
    public record LolDeepStats(String queue, int games, int gamesFound, int gamesWithFullData, int wins,
                               DeepOverview overview, List<DeepChampionStats> champions, List<DeepRoleStats> roles,
                               List<DuoStats> duos, List<MatchupStats> matchups, List<ChampionGames> enemyChampions,
                               List<ChampionGames> allyChampions, List<SideStats> sides, List<BucketStats> durations,
                               List<BucketStats> hours, List<WeekdayStats> weekdays, ObjectiveStats objectives,
                               List<ItemStats> items, List<KeystoneStats> keystones, List<SpellStats> spells,
                               List<PatchStats> patches, List<TrendPoint> trend) {
    }

    /**
     * Over every game in the window; the numbers only whole matches have (wards killed, control wards, solo kills,
     * crowd control, time dead, objective damage, first blood, multi kills) over the whole ones only.
     *
     * @param deadShare      time spent dead over game time, 0..1
     * @param damagePerGold  champion damage per gold earned
     * @param firstBloodRate share of games with a hand in the first kill, 0..1
     * @param multiKills     totals, not averages
     */
    public record DeepOverview(BigDecimal kda, BigDecimal avgKills, BigDecimal avgDeaths, BigDecimal avgAssists,
                               BigDecimal killParticipation, BigDecimal damageShare, BigDecimal csPerMinute,
                               BigDecimal goldPerMinute, BigDecimal damagePerMinute, BigDecimal damageTakenPerMinute,
                               BigDecimal visionPerMinute, BigDecimal avgWardsPlaced, BigDecimal avgWardsKilled,
                               BigDecimal avgControlWards, BigDecimal avgSoloKills, BigDecimal avgCcTime,
                               BigDecimal deadShare, BigDecimal damagePerGold, BigDecimal objectiveDamagePerMinute,
                               BigDecimal firstBloodRate, MultiKills multiKills, BigDecimal avgGameMinutes) {
    }

    /** "double" is a Java keyword, so the components carry the JSON names explicitly. */
    public record MultiKills(@JsonProperty("double") int doubleKills, @JsonProperty("triple") int tripleKills,
                             @JsonProperty("quadra") int quadraKills, @JsonProperty("penta") int pentaKills) {
    }

    /**
     * @param kills      average per game, like deaths and assists
     * @param pentas     pentakills in these games; for a game not stored whole, one when its largest multi kill was
     *                   a penta
     * @param lastPlayed when the newest of these games ended
     */
    public record DeepChampionStats(String champion, int games, int wins, BigDecimal kills, BigDecimal deaths,
                                    BigDecimal assists, BigDecimal kda, BigDecimal csPerMinute,
                                    BigDecimal damagePerMinute, BigDecimal goldPerMinute, BigDecimal visionPerMinute,
                                    BigDecimal killParticipation, BigDecimal damageShare, int pentas,
                                    LocalDateTime lastPlayed) {
    }

    public record DeepRoleStats(String position, int games, int wins, BigDecimal kda, BigDecimal csPerMinute,
                                BigDecimal damagePerMinute, BigDecimal visionPerMinute) {
    }

    /**
     * A teammate seen in at least two of the games.
     *
     * @param riotName   as of the newest game together (Riot IDs can change)
     * @param kda        theirs, over the games together
     * @param myKda      the followed player's own, over the same games
     * @param champions  their three most played champions in those games
     * @param tracked    the viewer can open their player page (follows them or has them in their group)
     */
    public record DuoStats(String puuid, String riotName, String riotTag, int games, int wins, BigDecimal kda,
                           BigDecimal myKda, List<String> champions, boolean tracked) {
    }

    /**
     * Games against one lane opponent champion (the enemy with the same position).
     *
     * @param kda         the followed player's own in those games
     * @param avgGoldDiff end-of-game gold, mine minus theirs
     * @param avgCsDiff   end-of-game cs, mine minus theirs
     */
    public record MatchupStats(String champion, int games, int wins, BigDecimal kda, BigDecimal avgGoldDiff,
                               BigDecimal avgCsDiff) {
    }

    /** @param wins games the followed player won with (or against) this champion in the game */
    public record ChampionGames(String champion, int games, int wins) {
    }

    /** @param teamId 100 blue, 200 red */
    public record SideStats(int teamId, int games, int wins) {
    }

    /** A game length ("<25", "25-30", "30-35", "35+" minutes) or local time of day ("00-06" ... "18-24"). */
    public record BucketStats(String label, int games, int wins) {
    }

    /** @param day 1 Monday ... 7 Sunday, in the viewer's time zone */
    public record WeekdayStats(int day, int games, int wins) {
    }

    /**
     * The followed player's team, over the games stored whole; each average null when there are none.
     *
     * @param firstBlood games where the team got first blood, and how many of those were won; likewise the others
     */
    public record ObjectiveStats(BigDecimal avgTeamDragons, BigDecimal avgTeamBarons, BigDecimal avgTeamTowers,
                                 BigDecimal avgTeamGrubs, BigDecimal avgTeamHeralds, FirstStats firstBlood,
                                 FirstStats firstTower, FirstStats firstDragon, FirstStats firstBaron) {
    }

    public record FirstStats(int games, int wins) {
    }

    /** @param games games that ended with the item in one of the six slots (the trinket is left out) */
    public record ItemStats(int itemId, int games, int wins) {
    }

    public record KeystoneStats(Integer keystone, Integer primaryStyle, int games, int wins) {
    }

    /** @param spells the two summoner spell ids as Riot orders them, e.g. "4,14" */
    public record SpellStats(String spells, int games, int wins) {
    }

    /** @param patch major.minor, e.g. "14.20" */
    public record PatchStats(String patch, int games, int wins) {
    }

    /** One game for the trend charts; gold, vision and kill participation are null on rows without details. */
    public record TrendPoint(String matchId, LocalDateTime endedAt, boolean win, BigDecimal kda,
                             BigDecimal csPerMinute, BigDecimal damagePerMinute, BigDecimal goldPerMinute,
                             BigDecimal visionPerMinute, BigDecimal killParticipation) {
    }

    // ---- Match page (GET /lol/matches/{matchId}) ----

    /** @param timelineAvailable false only when Riot was asked for the timeline and no longer had it */
    public record LolMatchDetail(String matchId, int queueId, String gameMode, String gameVersion,
                                 LocalDateTime endedAt, int durationS, List<MatchTeam> teams,
                                 List<MatchParticipant> participants, boolean timelineAvailable) {
    }

    /** @param bans champion keys in pick order, -1 for a skipped ban */
    public record MatchTeam(int teamId, boolean win, int kills, int towers, int inhibitors, int dragons, int barons,
                            int heralds, int grubs, int atakhan, boolean firstBlood, boolean firstTower,
                            boolean firstDragon, boolean firstBaron, List<Integer> bans) {
    }

    /**
     * @param items   seven item ids, 0 for an empty slot, the last being the trinket
     * @param spells  two summoner spell ids
     * @param tracked the viewer can open this player's page (follows them or has them in their group)
     * @param lpChange LP this game moved for a followed player, when it could be estimated; null otherwise
     */
    public record MatchParticipant(int participantId, String puuid, String riotName, String riotTag, int teamId,
                                   String champion, int champLevel, String position, int kills, int deaths,
                                   int assists, int cs, int gold, int damage, int damageTaken, int damageObjectives,
                                   int damageBuildings, int healing, int shielding, int ccTime, int timeDead,
                                   int visionScore, int wardsPlaced, int wardsKilled, int controlWards,
                                   int doubleKills, int tripleKills, int quadraKills, int pentaKills,
                                   boolean firstBlood, Integer soloKills, BigDecimal killParticipation,
                                   BigDecimal damageShare, List<Integer> items, List<Integer> spells, Integer keystone,
                                   Integer primaryStyle, Integer secondaryStyle, boolean win, boolean tracked,
                                   Integer lpChange) {
    }

    // ---- Timeline (GET /lol/matches/{matchId}/timeline) ----

    public record LolTimeline(String matchId, List<TimelineMinute> minutes, List<TimelineParticipant> participants) {
    }

    /** Team gold at one minute, for the gold-lead graph. */
    public record TimelineMinute(int minute, int blueGold, int redGold) {
    }

    /**
     * @param at10 totals at minute 10; null when the game was shorter
     * @param gold total gold at each minute, from minute 0
     */
    public record TimelineParticipant(int participantId, FrameSnapshot at10, FrameSnapshot at15, List<Integer> gold) {
    }

    public record FrameSnapshot(int gold, int cs, int xp, int level) {
    }

    // ---- Group duos (GET /lol/group/duos) ----

    /** Two group members whose linked accounts played on one team; wins are that team's. */
    public record GroupDuo(GroupDuoMember a, GroupDuoMember b, int games, int wins) {
    }

    public record GroupDuoMember(Long userId, String displayName) {
    }
}

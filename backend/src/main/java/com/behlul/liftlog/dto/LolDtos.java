package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.LolMatchPlayer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public final class LolDtos {

    private LolDtos() {
    }

    /**
     * A Riot ID as players type it, "Name#TAG": a 3–16 character name (spaces allowed) and a 3–5 character
     * tag. The platform picks the server, e.g. TR1.
     */
    public record TrackPlayerRequest(
            @NotBlank @Pattern(regexp = "^[^#]{3,16}#[\\p{L}\\p{N}]{3,5}$", message = "must look like Name#TAG") String riotId,
            // Kept in step with LolPlatform; validated here so an unknown server is a 400, not a server error
            @NotBlank @Pattern(regexp = "(?i)TR1|EUW1|EUN1|RU|ME1|NA1|BR1|LA1|LA2|KR|JP1|OC1", message = "unknown platform") String platform
    ) {
    }

    /** Solo/duo rank; absent when the player is unranked. */
    public record RankResponse(String tier, String division, int lp, int wins, int losses) {
    }

    /**
     * @param refreshedAt when Riot was last asked; older data is still shown when Riot is unavailable
     * @param recentWins  wins in the stored recent matches, for the list's quick form indicator
     * @param summary     averages over the same matches, for the group table; null when none are stored
     */
    public record LolPlayerResponse(String puuid, String gameName, String tagLine, String platform, Integer profileIconId,
                                    Integer summonerLevel, RankResponse rank, int recentGames, int recentWins,
                                    LocalDateTime refreshedAt, LolPlayerSummary summary) {
    }

    /** The few averages the group table compares; see {@link LolSummary} for what each one means. */
    public record LolPlayerSummary(BigDecimal kda, BigDecimal killParticipation, BigDecimal csPerMinute,
                                   BigDecimal damagePerMinute, BigDecimal visionPerMinute) {
    }

    /**
     * Everything after {@code win} is null on matches stored before the details existed, until a refresh fills
     * them in.
     *
     * @param killParticipation 0..1
     * @param damageShare       share of the team's champion damage, 0..1
     * @param items             seven item ids, 0 for an empty slot; the last one is the trinket
     * @param summonerSpells    two summoner spell ids
     */
    public record LolMatchResponse(String matchId, int queueId, LocalDateTime endedAt, int durationS, String champion,
                                   String position, int kills, int deaths, int assists, int cs, int damage, boolean win,
                                   Integer gold, Integer vision, Integer champLevel, Integer damageTaken,
                                   BigDecimal killParticipation, BigDecimal damageShare, Integer largestMultiKill,
                                   Integer wardsPlaced, List<Integer> items, List<Integer> summonerSpells, String gameMode) {

        public static LolMatchResponse from(LolMatchPlayer m) {
            return new LolMatchResponse(m.getMatchId(), m.getQueueId(), m.getEndedAt(), m.getDurationS(), m.getChampion(),
                    m.getPosition(), m.getKills(), m.getDeaths(), m.getAssists(), m.getCs(), m.getDamage(), m.isWin(),
                    m.getGoldEarned(), m.getVisionScore(), m.getChampLevel(), m.getDamageTaken(), m.getKillParticipation(),
                    m.getDamageShare(), m.getLargestMultiKill(), m.getWardsPlaced(), ids(m.getItems()),
                    ids(m.getSummonerSpells()), m.getGameMode());
        }

        /** "3006,0,3031" to [3006, 0, 3031]; null stays null. */
        private static List<Integer> ids(String commaSeparated) {
            if (commaSeparated == null || commaSeparated.isBlank()) {
                return null;
            }
            return Arrays.stream(commaSeparated.split(",")).map(String::trim).map(Integer::valueOf).toList();
        }
    }

    public record ChampionStats(String champion, int games, int wins, BigDecimal kda, BigDecimal csPerMinute,
                                BigDecimal damagePerMinute) {
    }

    /** @param win whether the streak is of wins; count is how many games in a row, newest first */
    public record Streak(boolean win, int count) {
    }

    /** @param position Riot's teamPosition: TOP, JUNGLE, MIDDLE, BOTTOM or UTILITY */
    public record RoleStats(String position, int games, int wins) {
    }

    public record QueueStats(int queueId, int games, int wins) {
    }

    /** The match a record was set in, so the page can link to its row. */
    public record MatchRecord(String matchId, BigDecimal value) {
    }

    /** Each one null when no stored match has the number (largestMultiKill on old rows, or no games at all). */
    public record Records(MatchRecord mostKills, MatchRecord bestKda, MatchRecord mostDamage, MatchRecord mostCs,
                          MatchRecord longestGame, MatchRecord largestMultiKill) {
    }

    /**
     * Averages over the stored recent matches. kda is (kills + assists) / deaths, with zero deaths
     * counted as one so a deathless game does not divide by zero.
     *
     * <p>The detail averages (killParticipation to damageShare) only count matches that have the number, so old
     * rows without it do not drag them down; each is null when no match has it. Per-minute numbers are totals
     * over total minutes; killParticipation and damageShare are fractions 0..1.
     *
     * @param form      newest first, true for a win
     * @param champions every champion played, most played first
     * @param streak    null when there are no games
     * @param roles     most played first; games without a position are left out
     * @param queues    most played first
     */
    public record LolSummary(int games, int wins, BigDecimal avgKills, BigDecimal avgDeaths, BigDecimal avgAssists,
                             BigDecimal kda, BigDecimal csPerMinute, List<ChampionStats> champions, List<Boolean> form,
                             BigDecimal killParticipation, BigDecimal damagePerMinute, BigDecimal goldPerMinute,
                             BigDecimal visionPerMinute, BigDecimal damageShare, Streak streak, List<RoleStats> roles,
                             List<QueueStats> queues, Records records) {
    }

    /** One point of the LP chart. */
    public record RankHistoryPoint(LocalDateTime recordedAt, String tier, String division, int lp) {
    }

    public record LolPlayerDetailResponse(LolPlayerResponse player, LolSummary summary, List<LolMatchResponse> matches) {
    }

    public record GroupNameRequest(@NotBlank @Size(max = 32) String name) {
    }

    /** Any case, with the display dash or spaces; the service normalizes it. The web app extracts it from a pasted message first. */
    public record JoinGroupRequest(@NotBlank @Size(max = 20) String code) {
    }

    /** @param player the member's linked Riot account; null until they link one */
    public record LolGroupMemberResponse(Long userId, String displayName, boolean you, boolean owner,
                                         LocalDateTime joinedAt, LolPlayerResponse player) {
    }

    /** @param inviteCode 10 symbols without the dash; members come in join order, so the first one is the owner */
    public record LolGroupResponse(String name, String inviteCode, int maxMembers, List<LolGroupMemberResponse> members) {
    }
}

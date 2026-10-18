package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.LolMatchPlayer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
     */
    public record LolPlayerResponse(String puuid, String gameName, String tagLine, String platform, Integer profileIconId,
                                    Integer summonerLevel, RankResponse rank, int recentGames, int recentWins,
                                    LocalDateTime refreshedAt) {
    }

    public record LolMatchResponse(String matchId, int queueId, LocalDateTime endedAt, int durationS, String champion,
                                   String position, int kills, int deaths, int assists, int cs, int damage, boolean win) {

        public static LolMatchResponse from(LolMatchPlayer m) {
            return new LolMatchResponse(m.getMatchId(), m.getQueueId(), m.getEndedAt(), m.getDurationS(), m.getChampion(),
                    m.getPosition(), m.getKills(), m.getDeaths(), m.getAssists(), m.getCs(), m.getDamage(), m.isWin());
        }
    }

    public record ChampionStats(String champion, int games, int wins, BigDecimal kda) {
    }

    /**
     * Averages over the stored recent matches. kda is (kills + assists) / deaths, with zero deaths
     * counted as one so a deathless game does not divide by zero.
     *
     * @param form newest first, true for a win
     */
    public record LolSummary(int games, int wins, BigDecimal avgKills, BigDecimal avgDeaths, BigDecimal avgAssists,
                             BigDecimal kda, BigDecimal csPerMinute, List<ChampionStats> champions, List<Boolean> form) {
    }

    public record LolPlayerDetailResponse(LolPlayerResponse player, LolSummary summary, List<LolMatchResponse> matches) {
    }
}

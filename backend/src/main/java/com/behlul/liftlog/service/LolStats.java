package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos.ChampionStats;
import com.behlul.liftlog.dto.LolDtos.LolSummary;
import com.behlul.liftlog.entity.LolMatchPlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns stored match lines into the numbers the player page shows. Pure, so it is tested directly. */
final class LolStats {

    static final int TOP_CHAMPIONS = 3;
    static final int FORM_LENGTH = 10;

    private LolStats() {
    }

    /** @param matches newest first */
    static LolSummary summarize(List<LolMatchPlayer> matches) {
        int games = matches.size();
        int wins = (int) matches.stream().filter(LolMatchPlayer::isWin).count();
        int kills = matches.stream().mapToInt(LolMatchPlayer::getKills).sum();
        int deaths = matches.stream().mapToInt(LolMatchPlayer::getDeaths).sum();
        int assists = matches.stream().mapToInt(LolMatchPlayer::getAssists).sum();
        int cs = matches.stream().mapToInt(LolMatchPlayer::getCs).sum();
        int seconds = matches.stream().mapToInt(LolMatchPlayer::getDurationS).sum();

        return new LolSummary(games, wins, average(kills, games), average(deaths, games), average(assists, games),
                kda(kills, deaths, assists), seconds == 0 ? BigDecimal.ZERO : divide(cs * 60L, seconds),
                champions(matches), matches.stream().limit(FORM_LENGTH).map(LolMatchPlayer::isWin).toList());
    }

    static BigDecimal kda(int kills, int deaths, int assists) {
        return divide(kills + assists, Math.max(1, deaths));
    }

    /** Most played first; among equally played, the one won more often. */
    private static List<ChampionStats> champions(List<LolMatchPlayer> matches) {
        Map<String, List<LolMatchPlayer>> byChampion = new LinkedHashMap<>();
        for (LolMatchPlayer m : matches) {
            byChampion.computeIfAbsent(m.getChampion(), c -> new java.util.ArrayList<>()).add(m);
        }
        return byChampion.entrySet().stream()
                .map(e -> {
                    List<LolMatchPlayer> list = e.getValue();
                    return new ChampionStats(e.getKey(), list.size(), (int) list.stream().filter(LolMatchPlayer::isWin).count(),
                            kda(list.stream().mapToInt(LolMatchPlayer::getKills).sum(),
                                    list.stream().mapToInt(LolMatchPlayer::getDeaths).sum(),
                                    list.stream().mapToInt(LolMatchPlayer::getAssists).sum()));
                })
                .sorted(Comparator.comparingInt(ChampionStats::games).reversed().thenComparing(Comparator.comparingInt(ChampionStats::wins).reversed()))
                .limit(TOP_CHAMPIONS)
                .toList();
    }

    private static BigDecimal average(int total, int count) {
        return count == 0 ? BigDecimal.ZERO : divide(total, count);
    }

    private static BigDecimal divide(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}

package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos.ChampionStats;
import com.behlul.liftlog.dto.LolDtos.LolPlayerSummary;
import com.behlul.liftlog.dto.LolDtos.LolSummary;
import com.behlul.liftlog.dto.LolDtos.MatchRecord;
import com.behlul.liftlog.dto.LolDtos.QueueStats;
import com.behlul.liftlog.dto.LolDtos.Records;
import com.behlul.liftlog.dto.LolDtos.RoleStats;
import com.behlul.liftlog.dto.LolDtos.Streak;
import com.behlul.liftlog.entity.LolMatchPlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Turns stored match lines into the numbers the player page shows. Pure, so it is tested directly.
 *
 * <p>Matches stored before the detail columns existed have nulls there. Every number that needs a detail is
 * computed only over the matches that have it, so a half-backfilled history still gives honest averages.
 */
final class LolStats {

    static final int FORM_LENGTH = 10;
    /** Fractions (kill participation, damage share) keep four decimals: 0.5123 is 51.23 %. */
    private static final int FRACTION_SCALE = 4;

    private LolStats() {
    }

    /** @param matches newest first */
    static LolSummary summarize(List<LolMatchPlayer> matches) {
        int games = matches.size();
        int wins = wins(matches);
        int kills = sum(matches, LolMatchPlayer::getKills);
        int deaths = sum(matches, LolMatchPlayer::getDeaths);
        int assists = sum(matches, LolMatchPlayer::getAssists);
        BigDecimal csPerMinute = perMinute(matches, LolMatchPlayer::getCs);

        return new LolSummary(games, wins, average(kills, games), average(deaths, games), average(assists, games),
                kda(kills, deaths, assists), csPerMinute == null ? BigDecimal.ZERO : csPerMinute,
                champions(matches), matches.stream().limit(FORM_LENGTH).map(LolMatchPlayer::isWin).toList(),
                meanFraction(matches, LolMatchPlayer::getKillParticipation),
                perMinute(matches, LolMatchPlayer::getDamage),
                perMinute(matches, LolMatchPlayer::getGoldEarned),
                perMinute(matches, LolMatchPlayer::getVisionScore),
                meanFraction(matches, LolMatchPlayer::getDamageShare),
                streak(matches), roles(matches), queues(matches), records(matches));
    }

    /** The group table's columns; null when no matches are stored, so an empty row is not shown as zeros. */
    static LolPlayerSummary playerSummary(List<LolMatchPlayer> matches) {
        if (matches.isEmpty()) {
            return null;
        }
        return new LolPlayerSummary(
                kda(sum(matches, LolMatchPlayer::getKills), sum(matches, LolMatchPlayer::getDeaths),
                        sum(matches, LolMatchPlayer::getAssists)),
                meanFraction(matches, LolMatchPlayer::getKillParticipation),
                perMinute(matches, LolMatchPlayer::getCs),
                perMinute(matches, LolMatchPlayer::getDamage),
                perMinute(matches, LolMatchPlayer::getVisionScore));
    }

    static BigDecimal kda(int kills, int deaths, int assists) {
        return divide(kills + assists, Math.max(1, deaths));
    }

    /** Most played first; among equally played, the one won more often. */
    private static List<ChampionStats> champions(List<LolMatchPlayer> matches) {
        return groupBy(matches, LolMatchPlayer::getChampion).entrySet().stream()
                .map(e -> {
                    List<LolMatchPlayer> list = e.getValue();
                    return new ChampionStats(e.getKey(), list.size(), wins(list),
                            kda(sum(list, LolMatchPlayer::getKills), sum(list, LolMatchPlayer::getDeaths),
                                    sum(list, LolMatchPlayer::getAssists)),
                            perMinute(list, LolMatchPlayer::getCs), perMinute(list, LolMatchPlayer::getDamage));
                })
                .sorted(Comparator.comparingInt(ChampionStats::games).reversed()
                        .thenComparing(Comparator.comparingInt(ChampionStats::wins).reversed()))
                .toList();
    }

    /** The run the newest game belongs to: three wins in a row, or two losses. */
    private static Streak streak(List<LolMatchPlayer> matches) {
        if (matches.isEmpty()) {
            return null;
        }
        boolean win = matches.getFirst().isWin();
        int count = (int) matches.stream().takeWhile(m -> m.isWin() == win).count();
        return new Streak(win, count);
    }

    /** ARAM and some event modes have no position; those games are left out instead of shown as a role. */
    private static List<RoleStats> roles(List<LolMatchPlayer> matches) {
        List<LolMatchPlayer> withPosition = matches.stream().filter(m -> m.getPosition() != null).toList();
        return groupBy(withPosition, LolMatchPlayer::getPosition).entrySet().stream()
                .map(e -> new RoleStats(e.getKey(), e.getValue().size(), wins(e.getValue())))
                .sorted(Comparator.comparingInt(RoleStats::games).reversed())
                .toList();
    }

    private static List<QueueStats> queues(List<LolMatchPlayer> matches) {
        return groupBy(matches, LolMatchPlayer::getQueueId).entrySet().stream()
                .map(e -> new QueueStats(e.getKey(), e.getValue().size(), wins(e.getValue())))
                .sorted(Comparator.comparingInt(QueueStats::games).reversed())
                .toList();
    }

    private static Records records(List<LolMatchPlayer> matches) {
        return new Records(
                best(matches, m -> BigDecimal.valueOf(m.getKills())),
                best(matches, m -> kda(m.getKills(), m.getDeaths(), m.getAssists())),
                best(matches, m -> BigDecimal.valueOf(m.getDamage())),
                best(matches, m -> BigDecimal.valueOf(m.getCs())),
                best(matches, m -> BigDecimal.valueOf(m.getDurationS())),
                best(matches, m -> m.getLargestMultiKill() == null ? null : BigDecimal.valueOf(m.getLargestMultiKill())));
    }

    /** The highest value and its match; on a tie the newer match keeps it. Null when no match has the value. */
    private static MatchRecord best(List<LolMatchPlayer> matches, Function<LolMatchPlayer, BigDecimal> value) {
        MatchRecord best = null;
        for (LolMatchPlayer m : matches) {
            BigDecimal v = value.apply(m);
            if (v != null && (best == null || v.compareTo(best.value()) > 0)) {
                best = new MatchRecord(m.getMatchId(), v);
            }
        }
        return best;
    }

    /**
     * Total over total minutes, counting only the matches that have the value, so a long game weighs more than a
     * short one, as it does in the game's own stats. Null when no match has it.
     */
    private static BigDecimal perMinute(List<LolMatchPlayer> matches, Function<LolMatchPlayer, Integer> value) {
        long total = 0;
        long seconds = 0;
        for (LolMatchPlayer m : matches) {
            Integer v = value.apply(m);
            if (v != null) {
                total += v;
                seconds += m.getDurationS();
            }
        }
        return seconds == 0 ? null : divide(total * 60, seconds);
    }

    /** Plain mean of a 0..1 fraction over the matches that have it; null when none do. */
    private static BigDecimal meanFraction(List<LolMatchPlayer> matches, Function<LolMatchPlayer, BigDecimal> value) {
        List<BigDecimal> values = matches.stream().map(value).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), FRACTION_SCALE, RoundingMode.HALF_UP);
    }

    /** Keeps first-seen order, so equally ranked groups stay newest first after the stable sort. */
    private static <K> Map<K, List<LolMatchPlayer>> groupBy(List<LolMatchPlayer> matches, Function<LolMatchPlayer, K> key) {
        Map<K, List<LolMatchPlayer>> groups = new LinkedHashMap<>();
        for (LolMatchPlayer m : matches) {
            groups.computeIfAbsent(key.apply(m), k -> new ArrayList<>()).add(m);
        }
        return groups;
    }

    private static int wins(List<LolMatchPlayer> matches) {
        return (int) matches.stream().filter(LolMatchPlayer::isWin).count();
    }

    private static int sum(List<LolMatchPlayer> matches, ToIntFunction<LolMatchPlayer> value) {
        return matches.stream().mapToInt(value).sum();
    }

    private static BigDecimal average(int total, int count) {
        return count == 0 ? BigDecimal.ZERO : divide(total, count);
    }

    private static BigDecimal divide(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}

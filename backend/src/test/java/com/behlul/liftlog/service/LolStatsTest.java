package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos.LolPlayerSummary;
import com.behlul.liftlog.dto.LolDtos.LolSummary;
import com.behlul.liftlog.dto.LolDtos.MatchRecord;
import com.behlul.liftlog.dto.LolDtos.QueueStats;
import com.behlul.liftlog.dto.LolDtos.RoleStats;
import com.behlul.liftlog.dto.LolDtos.Streak;
import com.behlul.liftlog.entity.LolMatchPlayer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LolStatsTest {

    private static int nextId = 0;

    private static LolMatchPlayer match(String champion, int kills, int deaths, int assists, int cs, int seconds, boolean win) {
        return line(champion, "MIDDLE", 420, kills, deaths, assists, cs, seconds, 20_000, win);
    }

    /** A line as stored before the detail columns: all of them null. */
    private static LolMatchPlayer line(String champion, String position, int queueId, int kills, int deaths, int assists,
                                       int cs, int seconds, int damage, boolean win) {
        return new LolMatchPlayer("TR1_" + nextId++, "p", queueId, LocalDateTime.of(2026, 10, 18, 12, 0), seconds,
                champion, position, kills, deaths, assists, cs, damage, win);
    }

    private static LolMatchPlayer withDetails(LolMatchPlayer m, int gold, int vision, String killParticipation,
                                              String damageShare, int largestMultiKill) {
        m.fillDetails(new LolMatchPlayer.Details(gold, vision, 15, 20_000,
                killParticipation == null ? null : new BigDecimal(killParticipation),
                damageShare == null ? null : new BigDecimal(damageShare), largestMultiKill, 10,
                "3006,3031,0,0,0,0,3363", "4,14", "CLASSIC"));
        return m;
    }

    @Test
    void averagesKdaAndCsPerMinute() {
        LolSummary summary = LolStats.summarize(List.of(
                match("Ahri", 10, 2, 8, 200, 1_800, true),
                match("Ahri", 2, 6, 4, 160, 1_200, false)));

        assertThat(summary.games()).isEqualTo(2);
        assertThat(summary.wins()).isEqualTo(1);
        assertThat(summary.avgKills()).isEqualByComparingTo("6");
        // (12 + 12) / 8 deaths
        assertThat(summary.kda()).isEqualByComparingTo("3");
        // 360 cs over 50 minutes
        assertThat(summary.csPerMinute()).isEqualByComparingTo("7.2");
        assertThat(summary.form()).containsExactly(true, false);
    }

    @Test
    void deathlessGamesDoNotDivideByZero() {
        assertThat(LolStats.kda(5, 0, 7)).isEqualByComparingTo("12");
    }

    @Test
    void listsEveryChampionByGamesThenWins() {
        LolSummary summary = LolStats.summarize(List.of(
                match("Lux", 1, 1, 1, 100, 1_500, false),
                match("Jinx", 1, 1, 1, 100, 1_500, true),
                match("Lux", 2, 1, 1, 100, 1_500, false),
                match("Thresh", 1, 1, 1, 100, 1_500, true),
                match("Thresh", 3, 1, 1, 100, 1_500, true),
                match("Garen", 1, 1, 1, 100, 1_500, true)));

        // Jinx and Garen are tied on games and wins, so the more recent one (Jinx) stays ahead
        assertThat(summary.champions()).extracting(c -> c.champion()).containsExactly("Thresh", "Lux", "Jinx", "Garen");
        assertThat(summary.champions().getFirst().wins()).isEqualTo(2);
    }

    @Test
    void championRowsHaveCsAndDamagePerMinute() {
        LolSummary summary = LolStats.summarize(List.of(
                line("Ahri", "MIDDLE", 420, 5, 2, 5, 200, 1_800, 30_000, true),
                line("Ahri", "MIDDLE", 420, 1, 4, 3, 160, 1_200, 15_000, false)));

        assertThat(summary.champions()).singleElement().satisfies(c -> {
            assertThat(c.kda()).isEqualByComparingTo("2.33");
            // 360 cs and 45 000 damage over 50 minutes
            assertThat(c.csPerMinute()).isEqualByComparingTo("7.2");
            assertThat(c.damagePerMinute()).isEqualByComparingTo("900");
        });
    }

    @Test
    void detailAveragesLeaveOutOldRowsWithoutTheNumber() {
        LolSummary summary = LolStats.summarize(List.of(
                withDetails(line("Ahri", "MIDDLE", 420, 5, 2, 5, 200, 1_800, 30_000, true), 12_000, 30, "0.5", "0.2", 2),
                withDetails(line("Ahri", "MIDDLE", 420, 5, 2, 5, 200, 1_200, 15_000, true), 9_000, 20, "0.7", "0.3", 1),
                // Stored before the details: only its damage (always stored) counts
                line("Lux", "MIDDLE", 420, 5, 2, 5, 200, 1_500, 15_000, false)));

        assertThat(summary.killParticipation()).isEqualByComparingTo("0.6");
        assertThat(summary.damageShare()).isEqualByComparingTo("0.25");
        // 21 000 gold and 50 vision over the 50 minutes of the two detailed games
        assertThat(summary.goldPerMinute()).isEqualByComparingTo("420");
        assertThat(summary.visionPerMinute()).isEqualByComparingTo("1");
        // 60 000 damage over all 75 minutes
        assertThat(summary.damagePerMinute()).isEqualByComparingTo("800");
    }

    @Test
    void detailAveragesAreNullWhenNoMatchHasThem() {
        LolSummary summary = LolStats.summarize(List.of(
                match("Ahri", 3, 2, 1, 150, 1_500, true),
                // Details present, but Riot sent no challenges for this mode
                withDetails(match("Ahri", 3, 2, 1, 150, 1_500, true), 8_000, 10, null, null, 1)));

        assertThat(summary.killParticipation()).isNull();
        assertThat(summary.damageShare()).isNull();
        assertThat(summary.goldPerMinute()).isEqualByComparingTo("320");
        assertThat(summary.damagePerMinute()).isNotNull();

        LolSummary oldOnly = LolStats.summarize(List.of(match("Ahri", 3, 2, 1, 150, 1_500, true)));
        assertThat(oldOnly.goldPerMinute()).isNull();
        assertThat(oldOnly.visionPerMinute()).isNull();
        assertThat(oldOnly.records().largestMultiKill()).isNull();
    }

    @Test
    void streakCountsFromTheNewestGame() {
        assertThat(LolStats.summarize(List.of(
                match("Ahri", 1, 1, 1, 100, 1_500, true),
                match("Ahri", 1, 1, 1, 100, 1_500, true),
                match("Ahri", 1, 1, 1, 100, 1_500, false),
                match("Ahri", 1, 1, 1, 100, 1_500, true))).streak()).isEqualTo(new Streak(true, 2));
        assertThat(LolStats.summarize(List.of(
                match("Ahri", 1, 1, 1, 100, 1_500, false),
                match("Ahri", 1, 1, 1, 100, 1_500, false),
                match("Ahri", 1, 1, 1, 100, 1_500, false))).streak()).isEqualTo(new Streak(false, 3));
    }

    @Test
    void rolesSkipGamesWithoutAPositionAndQueuesCountEveryGame() {
        LolSummary summary = LolStats.summarize(List.of(
                line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 1_500, 10_000, true),
                line("Thresh", "UTILITY", 440, 1, 1, 1, 100, 1_500, 10_000, false),
                line("Lux", null, 450, 1, 1, 1, 100, 1_500, 10_000, true),
                line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 1_500, 10_000, false),
                line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 1_500, 10_000, true)));

        assertThat(summary.roles()).containsExactly(new RoleStats("MIDDLE", 3, 2), new RoleStats("UTILITY", 1, 0));
        assertThat(summary.queues()).containsExactly(new QueueStats(420, 3, 2), new QueueStats(440, 1, 0),
                new QueueStats(450, 1, 1));
    }

    @Test
    void recordsPointAtTheirMatchAndTiesStayWithTheNewer() {
        LolMatchPlayer newest = withDetails(line("Ahri", "MIDDLE", 420, 12, 3, 4, 150, 1_500, 25_000, true), 10_000, 20,
                "0.5", "0.2", 3);
        LolMatchPlayer bestKda = withDetails(line("Lux", "MIDDLE", 420, 6, 0, 14, 120, 1_400, 18_000, true), 9_000, 25,
                "0.6", "0.3", 1);
        LolMatchPlayer bigGame = line("Jinx", "BOTTOM", 420, 12, 8, 2, 310, 2_400, 41_000, false);

        LolSummary summary = LolStats.summarize(List.of(newest, bestKda, bigGame));

        assertThat(summary.records().mostKills()).isEqualTo(new MatchRecord(newest.getMatchId(), BigDecimal.valueOf(12)));
        assertThat(summary.records().bestKda().matchId()).isEqualTo(bestKda.getMatchId());
        assertThat(summary.records().bestKda().value()).isEqualByComparingTo("20");
        assertThat(summary.records().mostDamage().matchId()).isEqualTo(bigGame.getMatchId());
        assertThat(summary.records().mostCs().value()).isEqualByComparingTo("310");
        assertThat(summary.records().longestGame()).isEqualTo(new MatchRecord(bigGame.getMatchId(), BigDecimal.valueOf(2_400)));
        // The old row has no multikill number, so the record comes from a detailed game
        assertThat(summary.records().largestMultiKill()).isEqualTo(new MatchRecord(newest.getMatchId(), BigDecimal.valueOf(3)));
    }

    @Test
    void emptyHistoryGivesZerosAndNoDetails() {
        LolSummary summary = LolStats.summarize(List.of());
        assertThat(summary.games()).isZero();
        assertThat(summary.csPerMinute()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.champions()).isEmpty();
        assertThat(summary.damagePerMinute()).isNull();
        assertThat(summary.killParticipation()).isNull();
        assertThat(summary.streak()).isNull();
        assertThat(summary.roles()).isEmpty();
        assertThat(summary.queues()).isEmpty();
        assertThat(summary.records().mostKills()).isNull();
        assertThat(summary.records().longestGame()).isNull();
    }

    @Test
    void playerSummaryForTheGroupTable() {
        assertThat(LolStats.playerSummary(List.of())).isNull();

        LolPlayerSummary summary = LolStats.playerSummary(List.of(
                withDetails(line("Ahri", "MIDDLE", 420, 10, 2, 8, 200, 1_800, 30_000, true), 12_000, 30, "0.5", "0.2", 2),
                line("Ahri", "MIDDLE", 420, 2, 6, 4, 160, 1_200, 15_000, false)));

        assertThat(summary.kda()).isEqualByComparingTo("3");
        assertThat(summary.killParticipation()).isEqualByComparingTo("0.5");
        assertThat(summary.csPerMinute()).isEqualByComparingTo("7.2");
        assertThat(summary.damagePerMinute()).isEqualByComparingTo("900");
        // Vision only from the detailed 30 minutes
        assertThat(summary.visionPerMinute()).isEqualByComparingTo("1");
    }
}

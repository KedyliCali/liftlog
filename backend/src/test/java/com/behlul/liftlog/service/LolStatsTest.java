package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos.LolSummary;
import com.behlul.liftlog.entity.LolMatchPlayer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LolStatsTest {

    private static LolMatchPlayer match(String champion, int kills, int deaths, int assists, int cs, int seconds, boolean win) {
        return new LolMatchPlayer("TR1_" + champion + kills, "p", 420, LocalDateTime.of(2026, 10, 18, 12, 0), seconds,
                champion, "MIDDLE", kills, deaths, assists, cs, 20_000, win);
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
    void ranksChampionsByGamesThenWins() {
        LolSummary summary = LolStats.summarize(List.of(
                match("Lux", 1, 1, 1, 100, 1_500, false),
                match("Jinx", 1, 1, 1, 100, 1_500, true),
                match("Lux", 2, 1, 1, 100, 1_500, false),
                match("Thresh", 1, 1, 1, 100, 1_500, true),
                match("Thresh", 3, 1, 1, 100, 1_500, true),
                match("Garen", 1, 1, 1, 100, 1_500, true)));

        assertThat(summary.champions()).extracting(c -> c.champion()).containsExactly("Thresh", "Lux", "Jinx");
        assertThat(summary.champions().getFirst().wins()).isEqualTo(2);
    }

    @Test
    void emptyHistoryGivesZeros() {
        LolSummary summary = LolStats.summarize(List.of());
        assertThat(summary.games()).isZero();
        assertThat(summary.csPerMinute()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.champions()).isEmpty();
    }
}

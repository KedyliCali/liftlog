package com.behlul.liftlog.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WeekStreakTest {

    // Wednesday; its ISO week starts on Monday 2026-10-12
    private static final LocalDate TODAY = LocalDate.parse("2026-10-14");

    @Test
    void noWorkoutsMeansNoStreak() {
        assertThat(WeekStreak.compute(List.of(), TODAY)).isEqualTo(new WeekStreak(0, 0, 0));
    }

    @Test
    void countsTheCurrentWeekWhenItHasAWorkout() {
        WeekStreak streak = WeekStreak.compute(dates("2026-10-12", "2026-10-13", "2026-10-06", "2026-09-29"), TODAY);

        assertThat(streak).isEqualTo(new WeekStreak(3, 2, 3));
    }

    @Test
    void anEmptyCurrentWeekDoesNotBreakTheStreakYet() {
        // Monday morning before training: last week and the one before still count
        WeekStreak streak = WeekStreak.compute(dates("2026-10-11", "2026-10-01"), LocalDate.parse("2026-10-12"));

        assertThat(streak).isEqualTo(new WeekStreak(2, 0, 2));
    }

    @Test
    void aMissedWeekEndsTheStreak() {
        // Nothing in the week of 2026-10-05 or this week, so the old run is history
        WeekStreak streak = WeekStreak.compute(dates("2026-09-29", "2026-09-22"), TODAY);

        assertThat(streak.weeks()).isZero();
        assertThat(streak.longestWeeks()).isEqualTo(2);
    }

    @Test
    void sundayAndMondayAreDifferentIsoWeeks() {
        WeekStreak streak = WeekStreak.compute(dates("2026-10-11", "2026-10-12"), TODAY);

        assertThat(streak.weeks()).isEqualTo(2);
        assertThat(streak.thisWeekWorkouts()).isEqualTo(1);
    }

    @Test
    void longestRunCanBeOlderThanTheCurrentOne() {
        WeekStreak streak = WeekStreak.compute(dates(
                "2026-08-03", "2026-08-10", "2026-08-17", "2026-08-24", // 4 weeks in a row
                "2026-10-06", "2026-10-13"), TODAY);

        assertThat(streak).isEqualTo(new WeekStreak(2, 1, 4));
    }

    @Test
    void streakCrossesTheYearBoundary() {
        // ISO week 1 of 2026 starts on Monday 2025-12-29
        WeekStreak streak = WeekStreak.compute(dates("2025-12-24", "2025-12-31", "2026-01-05"),
                LocalDate.parse("2026-01-07"));

        assertThat(streak.weeks()).isEqualTo(3);
    }

    private static List<LocalDate> dates(String... isoDates) {
        return Stream.of(isoDates).map(LocalDate::parse).toList();
    }
}

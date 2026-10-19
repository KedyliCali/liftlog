package com.behlul.liftlog.service;

import com.behlul.liftlog.service.WeightTrend.Point;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeightTrendTest {

    private static final LocalDate LATEST = LocalDate.parse("2026-10-19");

    @Test
    void noEntriesMeansNoTrend() {
        assertThat(WeightTrend.compute(List.of())).isEqualTo(new WeightTrend(null, null, null, null));
    }

    @Test
    void aSingleEntryIsItsOwnAverageWithoutChanges() {
        WeightTrend trend = WeightTrend.compute(List.of(point(0, "80.40")));

        assertThat(trend.latest()).isEqualTo(point(0, "80.40"));
        assertThat(trend.change7d()).isNull();
        assertThat(trend.change30d()).isNull();
        assertThat(trend.average7d()).isEqualByComparingTo("80.40");
    }

    @Test
    void changesCompareWithTheEntryExactlyAWeekAndAMonthEarlier() {
        // Unsorted on purpose: the order the database returns them in must not matter
        WeightTrend trend = WeightTrend.compute(List.of(
                point(7, "81.00"), point(0, "80.20"), point(30, "83.50"), point(3, "80.60")));

        assertThat(trend.latest().date()).isEqualTo(LATEST);
        assertThat(trend.change7d()).isEqualByComparingTo("-0.80");
        assertThat(trend.change30d()).isEqualByComparingTo("-3.30");
    }

    @Test
    void aLateWeighInStillCountsButOnlyUpToTwiceTheSpan() {
        // Nothing exactly 7 days back: day 10 is the newest one at least a week old
        WeightTrend late = WeightTrend.compute(List.of(point(0, "80.00"), point(10, "79.00"), point(12, "78.00")));
        assertThat(late.change7d()).isEqualByComparingTo("1.00");

        // 15 days back is more than twice a week, so there is no honest 7-day change
        WeightTrend stale = WeightTrend.compute(List.of(point(0, "80.00"), point(15, "79.00")));
        assertThat(stale.change7d()).isNull();
        assertThat(stale.change30d()).isNull();
    }

    @Test
    void entriesNewerThanTheSpanAreNotABaseline() {
        // Day 6 is inside the week, so it cannot stand in for "a week ago"
        assertThat(WeightTrend.compute(List.of(point(0, "80.00"), point(6, "81.00"))).change7d()).isNull();
    }

    @Test
    void averageCoversTheSevenDaysEndingOnTheLatestEntry() {
        // Days 0..6 are in the window; day 7 is not
        WeightTrend trend = WeightTrend.compute(List.of(
                point(0, "80.00"), point(2, "81.00"), point(6, "82.50"), point(7, "90.00")));

        assertThat(trend.average7d()).isEqualByComparingTo(new BigDecimal("81.17")); // 243.5 / 3
    }

    private static Point point(int daysBeforeLatest, String kg) {
        return new Point(LATEST.minusDays(daysBeforeLatest), new BigDecimal(kg));
    }
}

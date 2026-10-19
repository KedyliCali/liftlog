package com.behlul.liftlog.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Body weight trend, measured from the newest weigh-in rather than from today, so a user who skipped a few
 * days still sees how far they moved instead of nothing.
 *
 * @param latest     the newest entry, null when there are none
 * @param change7d   latest minus the weight about a week earlier; null without such an entry
 * @param change30d  latest minus the weight about a month earlier; null without such an entry
 * @param average7d  mean of the entries in the 7 days ending on the latest one (2 decimals)
 */
public record WeightTrend(Point latest, BigDecimal change7d, BigDecimal change30d, BigDecimal average7d) {

    public record Point(LocalDate date, BigDecimal weightKg) {
    }

    static final WeightTrend EMPTY = new WeightTrend(null, null, null, null);

    /** @param points in any order, at most one per date */
    public static WeightTrend compute(List<Point> points) {
        if (points.isEmpty()) {
            return EMPTY;
        }
        List<Point> newestFirst = points.stream().sorted(Comparator.comparing(Point::date).reversed()).toList();
        Point latest = newestFirst.getFirst();
        return new WeightTrend(latest, change(newestFirst, latest, 7), change(newestFirst, latest, 30),
                average(newestFirst, latest, 7));
    }

    /**
     * Compares with the newest entry at least {@code days} before the latest. The baseline may be up to
     * twice that old (a weekly weigh-in that slipped a few days still counts) but no older: a 7-day change
     * measured against a weigh-in from three months ago would be misleading.
     */
    private static BigDecimal change(List<Point> newestFirst, Point latest, int days) {
        LocalDate notAfter = latest.date().minusDays(days);
        LocalDate notBefore = latest.date().minusDays(2L * days);
        return newestFirst.stream()
                .filter(p -> !p.date().isAfter(notAfter) && !p.date().isBefore(notBefore))
                .findFirst()
                .map(baseline -> latest.weightKg().subtract(baseline.weightKg()).setScale(2, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private static BigDecimal average(List<Point> newestFirst, Point latest, int days) {
        LocalDate firstDay = latest.date().minusDays(days - 1L);
        List<BigDecimal> window = newestFirst.stream()
                .filter(p -> !p.date().isBefore(firstDay))
                .map(Point::weightKg)
                .toList();
        BigDecimal sum = window.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP);
    }
}

package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.RoutineDtos.Suggestion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Double progression in its simplest form: when every planned set reached the target reps last time, add
 * 2.5 kg; when one fell short, repeat the weight. Pure, so it is tested directly.
 */
final class Progression {

    static final BigDecimal INCREASE_KG = new BigDecimal("2.50");
    private static final int SCALE = 2;

    private Progression() {
    }

    /** One set of the previous session: weightKg is the added load for bodyweight exercises. */
    record PreviousSet(int reps, BigDecimal weightKg) {
    }

    /**
     * @param suggestedWeightKg null on FIRST
     * @param increaseKg        only on a weight increase
     * @param suggestedReps     the target, or for a bodyweight-only exercise that progresses by reps: one more
     *                          than the lowest of last time's planned sets (never below the target)
     */
    record Result(Suggestion suggestion, BigDecimal suggestedWeightKg, BigDecimal lastWeightKg,
                  BigDecimal increaseKg, int suggestedReps) {
    }

    /** @param previous every set of this exercise in the previous session, in logged order; empty if never done */
    static Result suggest(List<PreviousSet> previous, int targetReps, int plannedSets, boolean bodyweight) {
        if (previous.isEmpty()) {
            return new Result(Suggestion.FIRST, null, null, null, targetReps);
        }
        BigDecimal workingWeight = workingWeight(previous);
        if (!reachedEveryPlannedSet(previous, targetReps, plannedSets)) {
            return new Result(Suggestion.SAME, workingWeight, workingWeight, null, targetReps);
        }
        // Without added load there is nothing to add 2.5 kg to: a bodyweight exercise progresses by one rep.
        // Counting from what was actually done (not the fixed target) lets the reps keep climbing session by session.
        if (bodyweight && workingWeight.signum() == 0) {
            int lowestPlannedReps = previous.stream().limit(plannedSets).mapToInt(PreviousSet::reps).min().orElseThrow();
            return new Result(Suggestion.INCREASE, workingWeight, workingWeight, null,
                    Math.max(lowestPlannedReps + 1, targetReps));
        }
        BigDecimal increased = workingWeight.add(INCREASE_KG).setScale(SCALE, RoundingMode.HALF_UP);
        return new Result(Suggestion.INCREASE, increased, workingWeight, INCREASE_KG, targetReps);
    }

    /** Fewer sets than planned counts as falling short, and only the first plannedSets sets are judged. */
    static boolean reachedEveryPlannedSet(List<PreviousSet> previous, int targetReps, int plannedSets) {
        return previous.size() >= plannedSets
                && previous.stream().limit(plannedSets).allMatch(set -> set.reps() >= targetReps);
    }

    /** The most common weight of the session (a warm-up or a single back-off set does not move it); ties: heaviest. */
    static BigDecimal workingWeight(List<PreviousSet> previous) {
        // stripTrailingZeros so 100 and 100.00 count as the same weight
        Map<BigDecimal, Long> counts = previous.stream()
                .collect(Collectors.groupingBy(set -> set.weightKg().stripTrailingZeros(), Collectors.counting()));
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<BigDecimal, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .map(weight -> weight.setScale(SCALE, RoundingMode.HALF_UP))
                .orElseThrow();
    }
}

package com.behlul.liftlog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class StatsDtos {

    private StatsDtos() {
    }

    /** Heaviest set and best estimated 1RM for one exercise. */
    public record PersonalRecordResponse(
            Long exerciseId,
            String exerciseName,
            BigDecimal heaviestWeightKg,
            int heaviestReps,
            LocalDate heaviestDate,
            BigDecimal bestEstimated1RmKg,
            LocalDate bestEstimated1RmDate,
            int totalSets,
            /* For bodyweight exercises heaviestWeightKg includes body weight; this is the extra load alone */
            BigDecimal heaviestAddedKg,
            boolean bodyweight
    ) {
    }

    /** One workout day for a single exercise. */
    public record ProgressPointResponse(LocalDate date, BigDecimal topWeightKg, BigDecimal estimated1RmKg,
                                        BigDecimal volumeKg) {
    }

    /** Consecutive ISO weeks with at least one workout; see {@code WeekStreak}. */
    public record StreakResponse(int weeks, int thisWeekWorkouts, int longestWeeks) {
    }

    public record WeeklyVolumeResponse(LocalDate weekStart, BigDecimal volumeKg, long setCount) {
    }

    /**
     * Sets per muscle over the user's workouts dated {@code from}..{@code to} (both inclusive).
     *
     * @param muscles only muscles with sets above zero, most sets first
     */
    public record MuscleStatsResponse(int days, LocalDate from, LocalDate to, List<MuscleSetsResponse> muscles) {
    }

    /**
     * @param muscle    one of the 16 map regions
     * @param sets      a set counts 1 for a primary muscle and 0.5 for a secondary one; one decimal
     * @param exercises what the sets came from, most sets first
     */
    public record MuscleSetsResponse(String muscle, BigDecimal sets, List<ExerciseSetsResponse> exercises) {
    }

    public record ExerciseSetsResponse(String name, BigDecimal sets) {
    }
}

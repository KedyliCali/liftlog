package com.behlul.liftlog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    public record WeeklyVolumeResponse(LocalDate weekStart, BigDecimal volumeKg, long setCount) {
    }
}

package com.behlul.liftlog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One card in the workout list. Built directly by a JPQL constructor query. */
public record WorkoutSummaryResponse(
        Long id,
        LocalDate workoutDate,
        String notes,
        long setCount,
        long exerciseCount,
        BigDecimal totalVolumeKg
) {
}

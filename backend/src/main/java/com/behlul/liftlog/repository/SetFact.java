package com.behlul.liftlog.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Flat, read-only view of one set used by the statistics queries (no entity graph to load).
 *
 * @param weightKg      effective load: the logged weight, plus the user's body weight for bodyweight exercises
 * @param addedWeightKg the weight as logged (for bodyweight exercises: the extra load on a belt or vest)
 */
public record SetFact(Long setId, Long exerciseId, String exerciseName, LocalDate workoutDate, int reps,
                      BigDecimal weightKg, BigDecimal addedWeightKg, boolean bodyweight) {

    /** Constructor for weighted exercises and tests, where the logged weight is the whole load. */
    public SetFact(Long setId, Long exerciseId, String exerciseName, LocalDate workoutDate, int reps, BigDecimal weightKg) {
        this(setId, exerciseId, exerciseName, workoutDate, reps, weightKg, weightKg, false);
    }
}

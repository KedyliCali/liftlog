package com.behlul.liftlog.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Flat, read-only view of one set used by the statistics queries (no entity graph to load). */
public record SetFact(Long setId, Long exerciseId, String exerciseName, LocalDate workoutDate, int reps,
                      BigDecimal weightKg) {
}

package com.behlul.liftlog.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One logged set, flattened with its workout and exercise for the CSV export. */
public record ExportedSet(LocalDate workoutDate, String workoutNotes, String exerciseName, int setOrder,
                          BigDecimal weightKg, int reps, BigDecimal rpe, boolean bodyweight) {
}

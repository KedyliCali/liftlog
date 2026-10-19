package com.behlul.liftlog.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Volume and set count of one exercise inside one workout; workouts without sets have no rows. */
public record WorkoutExerciseVolume(Long workoutId, LocalDate workoutDate, Long routineId, Long exerciseId,
                                    long setCount, BigDecimal volumeKg) {
}

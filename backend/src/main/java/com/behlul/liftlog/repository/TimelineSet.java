package com.behlul.liftlog.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One set placed on the user's training timeline (date, then workout id, then set order), so records can
 * be judged "at their time".
 *
 * @param loadKg effective load: the logged weight, plus the user's body weight for bodyweight exercises
 */
public record TimelineSet(Long workoutId, LocalDate workoutDate, int setOrder, Long exerciseId, String exerciseName,
                          int reps, BigDecimal loadKg) {
}

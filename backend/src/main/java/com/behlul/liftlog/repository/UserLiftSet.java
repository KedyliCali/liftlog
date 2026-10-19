package com.behlul.liftlog.repository;

import java.math.BigDecimal;

/** One set of a built-in exercise with its effective load, for estimating a user's 1RM. */
public record UserLiftSet(Long userId, String exerciseName, int reps, BigDecimal weightKg) {
}

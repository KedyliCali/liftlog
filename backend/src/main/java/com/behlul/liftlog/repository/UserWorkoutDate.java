package com.behlul.liftlog.repository;

import java.time.LocalDate;

/** One workout's date and whose it is; the group training summary reads many users at once. */
public record UserWorkoutDate(Long userId, LocalDate workoutDate) {
}

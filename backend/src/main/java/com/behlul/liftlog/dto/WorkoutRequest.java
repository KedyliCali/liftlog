package com.behlul.liftlog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Used for both creating and updating a workout. */
public record WorkoutRequest(
        @NotNull LocalDate workoutDate,
        @Size(max = 2000) String notes
) {
}

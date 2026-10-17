package com.behlul.liftlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateExerciseRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String muscleGroup
) {
}

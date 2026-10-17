package com.behlul.liftlog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Used for both adding and updating a set. Limits mirror the database CHECK constraints. */
public record SetRequest(
        @NotNull Long exerciseId,
        @NotNull @Min(1) @Max(1000) Integer reps,
        @NotNull @DecimalMin("0.0") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2) BigDecimal weightKg,
        @DecimalMin("1.0") @DecimalMax("10.0") @Digits(integer = 2, fraction = 1) BigDecimal rpe
) {
}

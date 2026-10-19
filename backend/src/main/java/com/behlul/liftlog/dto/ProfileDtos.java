package com.behlul.liftlog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    /**
     * @param avatarVersion changes whenever the photo changes (null when there is none); clients use
     *                      it as a cache key so they only download the image when it is new
     * @param shareTraining whether the user's group may see their training summary
     */
    public record ProfileResponse(Long id, String displayName, BigDecimal heightCm, BigDecimal bodyWeightKg,
                                  Long avatarVersion, boolean shareTraining) {
    }

    /** Boolean, not boolean: a missing field must be a 400, not a silent "off". */
    public record ShareTrainingRequest(@NotNull Boolean enabled) {
    }

    /** Full replacement: a null field clears that value. Limits mirror the database CHECK constraints. */
    public record UpdateProfileRequest(
            @Size(max = 100) String displayName,
            @DecimalMin("50.0") @DecimalMax("272.0") @Digits(integer = 3, fraction = 1) BigDecimal heightCm,
            @DecimalMin("20.0") @DecimalMax("500.0") @Digits(integer = 3, fraction = 2) BigDecimal bodyWeightKg
    ) {
    }
}

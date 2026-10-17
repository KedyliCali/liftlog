package com.behlul.liftlog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    /**
     * @param avatarVersion changes whenever the photo changes (null when there is none); clients use
     *                      it as a cache key so they only download the image when it is new
     */
    public record ProfileResponse(Long id, String displayName, BigDecimal heightCm, BigDecimal bodyWeightKg,
                                  Long avatarVersion) {
    }

    /** Full replacement: a null field clears that value. Limits mirror the database CHECK constraints. */
    public record UpdateProfileRequest(
            @Size(max = 100) String displayName,
            @DecimalMin("50.0") @DecimalMax("272.0") @Digits(integer = 3, fraction = 1) BigDecimal heightCm,
            @DecimalMin("20.0") @DecimalMax("500.0") @Digits(integer = 3, fraction = 2) BigDecimal bodyWeightKg
    ) {
    }
}

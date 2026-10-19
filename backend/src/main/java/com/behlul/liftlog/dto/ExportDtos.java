package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.User;

import java.math.BigDecimal;

public final class ExportDtos {

    private ExportDtos() {
    }

    /**
     * profile.json in the data export. Built field by field on purpose: email, password hash, token
     * version and passkey handle must never leave the server, even if User gains new fields later.
     */
    public record ExportedProfile(String displayName, BigDecimal heightCm, BigDecimal bodyWeightKg,
                                  Integer calorieGoalKcal, Integer proteinGoalG) {

        public static ExportedProfile from(User user) {
            return new ExportedProfile(user.getDisplayName(), user.getHeightCm(), user.getBodyWeightKg(),
                    user.getCalorieGoalKcal(), user.getProteinGoalG());
        }
    }
}

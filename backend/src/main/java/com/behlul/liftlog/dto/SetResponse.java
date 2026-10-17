package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.WorkoutSet;

import java.math.BigDecimal;

/**
 * @param newPersonalRecord true only in the response to adding a set that beats the user's previous
 *                          best estimated 1RM for that exercise; always false elsewhere
 */
public record SetResponse(
        Long id,
        Long exerciseId,
        String exerciseName,
        int setOrder,
        int reps,
        BigDecimal weightKg,
        BigDecimal rpe,
        boolean newPersonalRecord
) {

    public static SetResponse from(WorkoutSet set) {
        return from(set, false);
    }

    public static SetResponse from(WorkoutSet set, boolean newPersonalRecord) {
        return new SetResponse(
                set.getId(),
                set.getExercise().getId(),
                set.getExercise().getName(),
                set.getSetOrder(),
                set.getReps(),
                set.getWeightKg(),
                set.getRpe(),
                newPersonalRecord);
    }
}

package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.WorkoutSet;

import java.math.BigDecimal;

public record SetResponse(
        Long id,
        Long exerciseId,
        String exerciseName,
        int setOrder,
        int reps,
        BigDecimal weightKg,
        BigDecimal rpe
) {

    public static SetResponse from(WorkoutSet set) {
        return new SetResponse(
                set.getId(),
                set.getExercise().getId(),
                set.getExercise().getName(),
                set.getSetOrder(),
                set.getReps(),
                set.getWeightKg(),
                set.getRpe());
    }
}

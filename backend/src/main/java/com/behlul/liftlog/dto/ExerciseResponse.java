package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.Exercise;

public record ExerciseResponse(Long id, String name, String muscleGroup, boolean custom, boolean bodyweight) {

    public static ExerciseResponse from(Exercise exercise) {
        return new ExerciseResponse(exercise.getId(), exercise.getName(), exercise.getMuscleGroup(), !exercise.isBuiltIn(),
                exercise.isBodyweight());
    }
}

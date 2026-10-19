package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.Exercise;

import java.util.List;

/**
 * @param equipment BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, or null for custom exercises
 * @param muscles   map regions the exercise trains: stored for built-ins, guessed from muscleGroup for custom ones
 */
public record ExerciseResponse(Long id, String name, String muscleGroup, boolean custom, boolean bodyweight,
                               String equipment, List<MuscleRoleResponse> muscles) {

    public static ExerciseResponse from(Exercise exercise, List<MuscleRoleResponse> muscles) {
        return new ExerciseResponse(exercise.getId(), exercise.getName(), exercise.getMuscleGroup(), !exercise.isBuiltIn(),
                exercise.isBodyweight(), exercise.getEquipment(), muscles);
    }

    /**
     * @param muscle one of the 16 map regions (e.g. CHEST, FRONT_DELTS)
     * @param role   PRIMARY (a set counts 1) or SECONDARY (a set counts 0.5)
     */
    public record MuscleRoleResponse(String muscle, String role) {
    }
}

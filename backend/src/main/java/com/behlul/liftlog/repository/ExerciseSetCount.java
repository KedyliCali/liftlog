package com.behlul.liftlog.repository;

/** How many sets of one exercise were done (in a date window or a workout); input of the muscle map. */
public record ExerciseSetCount(Long exerciseId, String exerciseName, String muscleGroup, long setCount) {
}

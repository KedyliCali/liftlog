package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.Workout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @param totalVolumeKg reps × load summed over all sets; bodyweight exercises add the user's body weight
 * @param routineId     the routine the workout was started from (its plan is at /workouts/{id}/plan), or null
 */
public record WorkoutDetailResponse(Long id, LocalDate workoutDate, String notes, BigDecimal totalVolumeKg,
                                    List<SetResponse> sets, Long routineId) {

    public static WorkoutDetailResponse from(Workout workout) {
        BigDecimal bodyWeight = workout.getUser().getBodyWeightKg();
        List<SetResponse> sets = workout.getSets().stream().map(SetResponse::from).toList();
        BigDecimal volume = workout.getSets().stream()
                .map(set -> set.effectiveLoadKg(bodyWeight).multiply(BigDecimal.valueOf(set.getReps())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // getId() on a lazy proxy does not hit the database
        Long routineId = workout.getRoutine() == null ? null : workout.getRoutine().getId();
        return new WorkoutDetailResponse(workout.getId(), workout.getWorkoutDate(), workout.getNotes(), volume, sets,
                routineId);
    }
}

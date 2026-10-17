package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.Workout;

import java.time.LocalDate;
import java.util.List;

public record WorkoutDetailResponse(Long id, LocalDate workoutDate, String notes, List<SetResponse> sets) {

    public static WorkoutDetailResponse from(Workout workout) {
        List<SetResponse> sets = workout.getSets().stream().map(SetResponse::from).toList();
        return new WorkoutDetailResponse(workout.getId(), workout.getWorkoutDate(), workout.getNotes(), sets);
    }
}

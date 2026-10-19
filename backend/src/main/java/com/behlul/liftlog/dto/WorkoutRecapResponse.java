package com.behlul.liftlog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * End-of-workout summary card (GET /workouts/{id}/summary). Named "recap" because
 * {@link WorkoutSummaryResponse} is already the workout list card.
 *
 * @param previousVolumeKg  total volume of the workout it is compared with, or null when there is none
 * @param previousWorkoutId the latest earlier workout of the same routine, else with exactly the same exercises
 * @param weekStreak        the user's current weekly streak
 * @param muscles           sets per muscle in this workout (see GET /stats/muscles), most sets first
 */
public record WorkoutRecapResponse(
        Long workoutId,
        LocalDate workoutDate,
        BigDecimal totalVolumeKg,
        long sets,
        long exercises,
        List<RecordResponse> personalRecords,
        BigDecimal previousVolumeKg,
        Long previousWorkoutId,
        LocalDate previousWorkoutDate,
        int weekStreak,
        List<MuscleResponse> muscles
) {

    /** @param sets 1 per set for a primary muscle, 0.5 for a secondary one; one decimal */
    public record MuscleResponse(String muscle, BigDecimal sets) {
    }

    /**
     * @param weightKg effective load (body weight included for bodyweight exercises)
     * @param kind     WEIGHT (heaviest ever) or ESTIMATED_1RM (best estimated one-rep max)
     */
    public record RecordResponse(String exerciseName, BigDecimal weightKg, int reps, String kind) {
    }
}

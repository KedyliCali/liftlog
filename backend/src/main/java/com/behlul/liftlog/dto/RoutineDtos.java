package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.Routine;
import com.behlul.liftlog.entity.RoutineExercise;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RoutineDtos {

    private RoutineDtos() {
    }

    /** Used for both creating and replacing a routine. Limits mirror the database CHECK constraints. */
    public record RoutineRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid RoutineExerciseRequest> exercises
    ) {
    }

    public record RoutineExerciseRequest(
            @NotNull Long exerciseId,
            @NotNull @Min(1) @Max(10) Integer sets,
            @NotNull @Min(1) @Max(100) Integer targetReps
    ) {
    }

    /** Optional body of "save this workout as a routine"; without a name the workout's note (or date) is used. */
    public record FromWorkoutRequest(@Size(max = 60) String name) {
    }

    /** Optional body of "start this routine"; without a date the workout is for today (UTC). */
    public record StartRoutineRequest(LocalDate workoutDate) {
    }

    public record RoutineResponse(Long id, String name, List<RoutineExerciseResponse> exercises) {

        public static RoutineResponse from(Routine routine) {
            return new RoutineResponse(routine.getId(), routine.getName(),
                    routine.getExercises().stream().map(RoutineExerciseResponse::from).toList());
        }
    }

    public record RoutineExerciseResponse(Long exerciseId, String name, boolean bodyweight, int sets, int targetReps) {

        static RoutineExerciseResponse from(RoutineExercise entry) {
            return new RoutineExerciseResponse(entry.getExercise().getId(), entry.getExercise().getName(),
                    entry.getExercise().isBodyweight(), entry.getSets(), entry.getTargetReps());
        }
    }

    /** INCREASE: last time every planned set reached the target; SAME: some fell short; FIRST: never done. */
    public enum Suggestion { INCREASE, SAME, FIRST }

    /**
     * One exercise of a workout's routine, with what is left to do and what to load. Planned rows are not
     * stored: remaining = plannedSets - loggedSets.
     *
     * @param loggedSets        sets of this exercise already logged in this workout
     * @param suggestedWeightKg weight to prefill (added load for bodyweight exercises); null on FIRST
     * @param lastWeightKg      the working weight of the previous session (its most common weight); null on FIRST
     * @param lastReps          reps of every set of the previous session, in order; empty on FIRST
     * @param increaseKg        2.5 on a weight increase, otherwise null
     * @param suggestedReps     reps to prefill: the target, or when a bodyweight-only exercise progresses by
     *                          reps instead of load, the lowest reps of last time's planned sets + 1
     */
    public record PlanItemResponse(Long exerciseId, String name, boolean bodyweight, int plannedSets, int loggedSets,
                                   int targetReps, BigDecimal suggestedWeightKg, BigDecimal lastWeightKg,
                                   List<Integer> lastReps, Suggestion suggestion, BigDecimal increaseKg,
                                   int suggestedReps) {
    }
}

package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.WorkoutRecapResponse;
import com.behlul.liftlog.dto.WorkoutRecapResponse.RecordResponse;
import com.behlul.liftlog.entity.Workout;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.StatsRepository;
import com.behlul.liftlog.repository.WorkoutRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import com.behlul.liftlog.service.WorkoutRecap.WorkoutShape;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Builds the end-of-workout summary; like the stats, everything is derived from the sets on each request. */
@Service
@Transactional(readOnly = true)
public class WorkoutRecapService {

    private final WorkoutRepository workoutRepository;
    private final StatsRepository statsRepository;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public WorkoutRecapService(WorkoutRepository workoutRepository, StatsRepository statsRepository,
                               CurrentUserProvider currentUser, Clock clock) {
        this.workoutRepository = workoutRepository;
        this.statsRepository = statsRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public WorkoutRecapResponse summary(Long workoutId) {
        Long userId = currentUser.currentUserId();
        // Owner-scoped lookup: another user's workout is a plain 404
        Workout workout = workoutRepository.findByIdAndUserId(workoutId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Workout", workoutId));

        List<WorkoutShape> workouts = WorkoutRecap.shapes(statsRepository.findWorkoutExerciseVolumes(userId));
        // A workout without sets has no volume rows, so it gets an empty shape of its own
        WorkoutShape current = workouts.stream()
                .filter(shape -> shape.id().equals(workoutId))
                .findFirst()
                .orElseGet(() -> new WorkoutShape(workoutId, workout.getWorkoutDate(),
                        workout.getRoutine() == null ? null : workout.getRoutine().getId(),
                        Set.of(), 0, BigDecimal.ZERO));

        List<RecordResponse> records = current.exerciseIds().isEmpty()
                ? List.of()
                : WorkoutRecap.personalRecords(statsRepository.findTimeline(userId, current.exerciseIds()), workoutId)
                        .stream()
                        .map(hit -> new RecordResponse(hit.exerciseName(), hit.weightKg(), hit.reps(), hit.kind().name()))
                        .toList();

        Optional<WorkoutShape> previous = WorkoutRecap.previousWorkout(current, workouts);
        WeekStreak streak = WeekStreak.compute(statsRepository.findWorkoutDates(userId), LocalDate.now(clock));

        return new WorkoutRecapResponse(
                workoutId,
                current.date(),
                current.volumeKg(),
                current.setCount(),
                current.exerciseIds().size(),
                records,
                previous.map(WorkoutShape::volumeKg).orElse(null),
                previous.map(WorkoutShape::id).orElse(null),
                previous.map(WorkoutShape::date).orElse(null),
                streak.weeks());
    }
}

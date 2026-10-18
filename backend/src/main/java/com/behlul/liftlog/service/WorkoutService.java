package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.PageResponse;
import com.behlul.liftlog.dto.SetRequest;
import com.behlul.liftlog.dto.SetResponse;
import com.behlul.liftlog.dto.WorkoutDetailResponse;
import com.behlul.liftlog.dto.WorkoutRequest;
import com.behlul.liftlog.dto.WorkoutSummaryResponse;
import com.behlul.liftlog.entity.Exercise;
import com.behlul.liftlog.entity.Workout;
import com.behlul.liftlog.entity.WorkoutSet;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.ExerciseRepository;
import com.behlul.liftlog.repository.StatsRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.repository.WorkoutRepository;
import com.behlul.liftlog.repository.WorkoutSetRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Every lookup is scoped to the current user, so another user's workout looks exactly like a
 * missing one (404) and its existence is never revealed.
 */
@Service
public class WorkoutService {

    static final int MAX_PAGE_SIZE = 100;

    private final WorkoutRepository workoutRepository;
    private final WorkoutSetRepository setRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final StatsRepository statsRepository;
    private final CurrentUserProvider currentUser;

    public WorkoutService(WorkoutRepository workoutRepository, WorkoutSetRepository setRepository,
                          ExerciseRepository exerciseRepository, UserRepository userRepository,
                          StatsRepository statsRepository, CurrentUserProvider currentUser) {
        this.workoutRepository = workoutRepository;
        this.setRepository = setRepository;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.statsRepository = statsRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkoutSummaryResponse> listWorkouts(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return PageResponse.from(workoutRepository.findSummariesByUserId(
                currentUser.currentUserId(), PageRequest.of(safePage, safeSize)));
    }

    @Transactional(readOnly = true)
    public WorkoutDetailResponse getWorkout(Long id) {
        return WorkoutDetailResponse.from(loadDetail(id));
    }

    @Transactional
    public WorkoutDetailResponse createWorkout(WorkoutRequest request) {
        Workout workout = new Workout(userRepository.getReferenceById(currentUser.currentUserId()),
                request.workoutDate(), normalizeNotes(request.notes()));
        return WorkoutDetailResponse.from(workoutRepository.save(workout));
    }

    @Transactional
    public WorkoutDetailResponse updateWorkout(Long id, WorkoutRequest request) {
        Workout workout = loadDetail(id);
        workout.setWorkoutDate(request.workoutDate());
        workout.setNotes(normalizeNotes(request.notes()));
        return WorkoutDetailResponse.from(workout);
    }

    @Transactional
    public void deleteWorkout(Long id) {
        Workout workout = workoutRepository.findByIdAndUserId(id, currentUser.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Workout", id));
        workoutRepository.delete(workout);
    }

    @Transactional
    public SetResponse addSet(Long workoutId, SetRequest request) {
        Workout workout = loadDetail(workoutId);
        Exercise exercise = loadExercise(request.exerciseId());
        // Read the previous best before saving, otherwise the new set would compare against itself
        BigDecimal previousBest = StatsService.bestEstimate(
                statsRepository.findFactsForExercise(currentUser.currentUserId(), exercise.getId()));

        WorkoutSet set = new WorkoutSet(exercise, workout.nextSetOrder(), request.reps(),
                request.weightKg(), request.rpe());
        workout.addSet(set);
        // Flush so the generated id is available for the response
        setRepository.saveAndFlush(set);

        // The very first set of an exercise is not announced as a record
        // Compare like the stats do: bodyweight sets count the lifter's body weight on top
        BigDecimal load = set.effectiveLoadKg(workout.getUser().getBodyWeightKg());
        boolean newRecord = previousBest != null
                && OneRepMax.estimate(load, set.getReps()).compareTo(previousBest) > 0;
        return SetResponse.from(set, newRecord);
    }

    @Transactional
    public SetResponse updateSet(Long setId, SetRequest request) {
        WorkoutSet set = loadSet(setId);
        set.setExercise(loadExercise(request.exerciseId()));
        set.setReps(request.reps());
        set.setWeightKg(request.weightKg());
        set.setRpe(request.rpe());
        return SetResponse.from(set);
    }

    @Transactional
    public void deleteSet(Long setId) {
        WorkoutSet set = loadSet(setId);
        set.getWorkout().removeSet(set);
    }

    private Workout loadDetail(Long id) {
        return workoutRepository.findDetailByIdAndUserId(id, currentUser.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Workout", id));
    }

    private WorkoutSet loadSet(Long id) {
        return setRepository.findOwnedById(id, currentUser.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Set", id));
    }

    private Exercise loadExercise(Long id) {
        return exerciseRepository.findVisibleById(id, currentUser.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Exercise", id));
    }

    private static String normalizeNotes(String notes) {
        return notes == null || notes.isBlank() ? null : notes.trim();
    }
}

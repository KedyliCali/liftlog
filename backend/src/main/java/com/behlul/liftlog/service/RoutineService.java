package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.RoutineDtos.FromWorkoutRequest;
import com.behlul.liftlog.dto.RoutineDtos.PlanItemResponse;
import com.behlul.liftlog.dto.RoutineDtos.RoutineExerciseRequest;
import com.behlul.liftlog.dto.RoutineDtos.RoutineRequest;
import com.behlul.liftlog.dto.RoutineDtos.RoutineResponse;
import com.behlul.liftlog.dto.RoutineDtos.StartRoutineRequest;
import com.behlul.liftlog.dto.WorkoutDetailResponse;
import com.behlul.liftlog.entity.Exercise;
import com.behlul.liftlog.entity.Routine;
import com.behlul.liftlog.entity.RoutineExercise;
import com.behlul.liftlog.entity.Workout;
import com.behlul.liftlog.entity.WorkoutSet;
import com.behlul.liftlog.exception.FieldValidationException;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.ExerciseRepository;
import com.behlul.liftlog.repository.RoutineRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.repository.WorkoutRepository;
import com.behlul.liftlog.repository.WorkoutSetRepository;
import com.behlul.liftlog.repository.WorkoutSetRepository.PreviousSetRow;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routines and the plan of a workout started from one. Like workouts, every lookup is scoped to the current
 * user, so another user's routine is a 404.
 */
@Service
public class RoutineService {

    static final int MAX_NAME_LENGTH = 60;
    static final int MAX_EXERCISES = 20;
    static final int MAX_SETS = 10;
    static final int MAX_TARGET_REPS = 100;

    private final RoutineRepository routineRepository;
    private final WorkoutRepository workoutRepository;
    private final WorkoutSetRepository setRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public RoutineService(RoutineRepository routineRepository, WorkoutRepository workoutRepository,
                          WorkoutSetRepository setRepository, ExerciseRepository exerciseRepository,
                          UserRepository userRepository, CurrentUserProvider currentUser, Clock clock) {
        this.routineRepository = routineRepository;
        this.workoutRepository = workoutRepository;
        this.setRepository = setRepository;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RoutineResponse> listRoutines() {
        return routineRepository.findAllWithExercisesByUserId(currentUser.currentUserId()).stream()
                .map(RoutineResponse::from)
                .toList();
    }

    @Transactional
    public RoutineResponse createRoutine(RoutineRequest request) {
        List<Exercise> exercises = visibleExercises(request.exercises());
        Routine routine = new Routine(userRepository.getReferenceById(currentUser.currentUserId()),
                request.name().trim());
        addAll(routine, request.exercises(), exercises);
        return RoutineResponse.from(routineRepository.save(routine));
    }

    @Transactional
    public RoutineResponse updateRoutine(Long id, RoutineRequest request) {
        Routine routine = loadRoutine(id);
        // Validate before touching the entity, so a rejected request changes nothing
        List<Exercise> exercises = visibleExercises(request.exercises());
        routine.setName(request.name().trim());
        routine.getExercises().clear();
        // Delete the old lines before inserting the new ones: positions are UNIQUE per routine
        routineRepository.flush();
        addAll(routine, request.exercises(), exercises);
        // Flush so the new lines get their ids inside this transaction
        routineRepository.flush();
        return RoutineResponse.from(routine);
    }

    @Transactional
    public void deleteRoutine(Long id) {
        // Workouts started from it keep their sets: the FK sets their routine_id to NULL
        routineRepository.delete(loadRoutine(id));
    }

    /**
     * Turns a logged workout into a routine: exercises in first-logged order, sets = how many were logged,
     * target reps = the most common reps (ties: the higher). Values are clamped to the routine limits.
     */
    @Transactional
    public RoutineResponse createFromWorkout(Long workoutId, FromWorkoutRequest request) {
        Workout workout = loadWorkout(workoutId);
        if (workout.getSets().isEmpty()) {
            throw new FieldValidationException("workout", "workout has no sets to save");
        }
        // LinkedHashMap keeps the order in which each exercise was first logged (sets come ordered by setOrder)
        Map<Exercise, List<WorkoutSet>> byExercise = workout.getSets().stream()
                .collect(Collectors.groupingBy(WorkoutSet::getExercise, LinkedHashMap::new, Collectors.toList()));

        Routine routine = new Routine(workout.getUser(), routineName(request, workout));
        byExercise.entrySet().stream().limit(MAX_EXERCISES).forEach(entry -> routine.addExercise(entry.getKey(),
                Math.min(entry.getValue().size(), MAX_SETS),
                Math.min(mostCommonReps(entry.getValue()), MAX_TARGET_REPS)));
        return RoutineResponse.from(routineRepository.save(routine));
    }

    /** Creates an empty workout linked to the routine; its notes start as the routine's name. */
    @Transactional
    public WorkoutDetailResponse startRoutine(Long id, StartRoutineRequest request) {
        Routine routine = loadRoutine(id);
        LocalDate date = request != null && request.workoutDate() != null
                ? request.workoutDate() : LocalDate.now(clock);
        Workout workout = new Workout(routine.getUser(), date, routine.getName(), routine);
        return WorkoutDetailResponse.from(workoutRepository.save(workout));
    }

    /** The routine of a workout minus what is already logged, with a progression suggestion per exercise. */
    @Transactional(readOnly = true)
    public List<PlanItemResponse> plan(Long workoutId) {
        Long userId = currentUser.currentUserId();
        Workout workout = loadWorkout(workoutId);
        if (workout.getRoutine() == null) {
            throw new ResourceNotFoundException("Plan of workout", workoutId);
        }
        Routine routine = routineRepository.findDetailByIdAndUserId(workout.getRoutine().getId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan of workout", workoutId));

        Set<Long> exerciseIds = routine.getExercises().stream()
                .map(entry -> entry.getExercise().getId())
                .collect(Collectors.toSet());
        // One query for the previous session of every exercise in the plan
        Map<Long, List<Progression.PreviousSet>> previousByExercise = setRepository
                .findPreviousSessionSets(userId, workout.getId(), workout.getWorkoutDate(), exerciseIds).stream()
                .collect(Collectors.groupingBy(PreviousSetRow::getExerciseId, Collectors.mapping(
                        row -> new Progression.PreviousSet(row.getReps(), row.getWeightKg()), Collectors.toList())));
        Map<Long, Long> loggedByExercise = workout.getSets().stream()
                .collect(Collectors.groupingBy(set -> set.getExercise().getId(), Collectors.counting()));

        return routine.getExercises().stream()
                .map(entry -> planItem(entry,
                        previousByExercise.getOrDefault(entry.getExercise().getId(), List.of()),
                        loggedByExercise.getOrDefault(entry.getExercise().getId(), 0L).intValue()))
                .toList();
    }

    private static PlanItemResponse planItem(RoutineExercise entry, List<Progression.PreviousSet> previous, int logged) {
        Exercise exercise = entry.getExercise();
        Progression.Result result = Progression.suggest(previous, entry.getTargetReps(), entry.getSets(),
                exercise.isBodyweight());
        return new PlanItemResponse(exercise.getId(), exercise.getName(), exercise.isBodyweight(), entry.getSets(),
                logged, entry.getTargetReps(), result.suggestedWeightKg(), result.lastWeightKg(),
                previous.stream().map(Progression.PreviousSet::reps).toList(), result.suggestion(),
                result.increaseKg(), result.suggestedReps());
    }

    private static void addAll(Routine routine, List<RoutineExerciseRequest> requested, List<Exercise> exercises) {
        for (int i = 0; i < requested.size(); i++) {
            routine.addExercise(exercises.get(i), requested.get(i).sets(), requested.get(i).targetReps());
        }
    }

    /**
     * Resolves the requested exercises in one query, in request order. An exercise the user cannot see (another
     * user's custom one, or a missing id) or one listed twice is a 400 pointing at its index.
     */
    private List<Exercise> visibleExercises(List<RoutineExerciseRequest> requested) {
        Set<Long> ids = requested.stream().map(RoutineExerciseRequest::exerciseId).collect(Collectors.toSet());
        Map<Long, Exercise> visible = exerciseRepository.findVisibleByIds(ids, currentUser.currentUserId()).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        List<Exercise> result = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            Long exerciseId = requested.get(i).exerciseId();
            String field = "exercises[" + i + "].exerciseId";
            Exercise exercise = visible.get(exerciseId);
            if (exercise == null) {
                throw new FieldValidationException(field, "exercise not found");
            }
            // The plan counts logged sets per exercise, so one exercise can appear only once
            if (!seen.add(exerciseId)) {
                throw new FieldValidationException(field, "exercise is already in the routine");
            }
            result.add(exercise);
        }
        return result;
    }

    private static String routineName(FromWorkoutRequest request, Workout workout) {
        String name;
        if (request != null && request.name() != null && !request.name().isBlank()) {
            name = request.name().trim();
        } else if (workout.getNotes() != null && !workout.getNotes().isBlank()) {
            name = workout.getNotes().trim();
        } else {
            name = workout.getWorkoutDate().toString();
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH).trim() : name;
    }

    /** Most common reps of the sets; ties: the higher, as the more ambitious target. */
    static int mostCommonReps(List<WorkoutSet> sets) {
        return sets.stream()
                .collect(Collectors.groupingBy(WorkoutSet::getReps, Collectors.counting()))
                .entrySet().stream()
                .max(Comparator.<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElseThrow();
    }

    private Routine loadRoutine(Long id) {
        return routineRepository.findDetailByIdAndUserId(id, currentUser.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Routine", id));
    }

    private Workout loadWorkout(Long id) {
        return workoutRepository.findDetailByIdAndUserId(id, currentUser.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Workout", id));
    }
}

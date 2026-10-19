package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.RoutineDtos.FromWorkoutRequest;
import com.behlul.liftlog.dto.RoutineDtos.RoutineExerciseRequest;
import com.behlul.liftlog.dto.RoutineDtos.RoutineRequest;
import com.behlul.liftlog.dto.RoutineDtos.RoutineResponse;
import com.behlul.liftlog.entity.Exercise;
import com.behlul.liftlog.entity.Routine;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.entity.Workout;
import com.behlul.liftlog.entity.WorkoutSet;
import com.behlul.liftlog.exception.FieldValidationException;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.ExerciseRepository;
import com.behlul.liftlog.repository.RoutineRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.repository.WorkoutRepository;
import com.behlul.liftlog.repository.WorkoutSetRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {

    private static final long USER_ID = 7L;

    @Mock private RoutineRepository routineRepository;
    @Mock private WorkoutRepository workoutRepository;
    @Mock private WorkoutSetRepository setRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUser;

    private RoutineService routineService;

    private final User user = new User("lifter@example.com", "hash", "Lifter");
    private final Exercise squat = exercise(1L, "Squat");
    private final Exercise bench = exercise(2L, "Bench Press");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
        routineService = new RoutineService(routineRepository, workoutRepository, setRepository, exerciseRepository,
                userRepository, currentUser, clock);
        lenient().when(currentUser.currentUserId()).thenReturn(USER_ID);
    }

    private static Exercise exercise(long id, String name) {
        Exercise exercise = new Exercise(name, "Legs", null);
        ReflectionTestUtils.setField(exercise, "id", id);
        return exercise;
    }

    @Test
    void createRejectsAnExerciseTheUserCannotSee() {
        when(exerciseRepository.findVisibleByIds(anyCollection(), eq(USER_ID))).thenReturn(List.of(squat));
        RoutineRequest request = new RoutineRequest("Legs", List.of(
                new RoutineExerciseRequest(1L, 3, 5), new RoutineExerciseRequest(99L, 3, 8)));

        assertThatThrownBy(() -> routineService.createRoutine(request))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(ex -> assertThat(((FieldValidationException) ex).fieldErrors())
                        .containsKey("exercises[1].exerciseId"));
        verify(routineRepository, never()).save(any());
    }

    @Test
    void createRejectsTheSameExerciseTwice() {
        when(exerciseRepository.findVisibleByIds(anyCollection(), eq(USER_ID))).thenReturn(List.of(squat));
        RoutineRequest request = new RoutineRequest("Legs", List.of(
                new RoutineExerciseRequest(1L, 3, 5), new RoutineExerciseRequest(1L, 2, 8)));

        assertThatThrownBy(() -> routineService.createRoutine(request))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(ex -> assertThat(((FieldValidationException) ex).fieldErrors())
                        .containsEntry("exercises[1].exerciseId", "exercise is already in the routine"));
    }

    @Test
    void createKeepsTheRequestOrderAndTrimsTheName() {
        when(exerciseRepository.findVisibleByIds(anyCollection(), eq(USER_ID))).thenReturn(List.of(squat, bench));
        when(routineRepository.save(any(Routine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RoutineRequest request = new RoutineRequest("  Push day ", List.of(
                new RoutineExerciseRequest(2L, 4, 6), new RoutineExerciseRequest(1L, 3, 5)));

        RoutineResponse response = routineService.createRoutine(request);

        assertThat(response.name()).isEqualTo("Push day");
        assertThat(response.exercises()).extracting("name").containsExactly("Bench Press", "Squat");
        assertThat(response.exercises().get(0).sets()).isEqualTo(4);
        assertThat(response.exercises().get(0).targetReps()).isEqualTo(6);
    }

    @Test
    void fromWorkoutGroupsSetsInFirstLoggedOrder() {
        Workout workout = new Workout(user, LocalDate.of(2026, 10, 18), " Leg day ");
        workout.addSet(new WorkoutSet(bench, 1, 8, new BigDecimal("60"), null));
        workout.addSet(new WorkoutSet(squat, 2, 5, new BigDecimal("100"), null));
        workout.addSet(new WorkoutSet(bench, 3, 6, new BigDecimal("60"), null));
        workout.addSet(new WorkoutSet(bench, 4, 6, new BigDecimal("60"), null));
        when(workoutRepository.findDetailByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(workout));
        when(routineRepository.save(any(Routine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoutineResponse response = routineService.createFromWorkout(5L, null);

        assertThat(response.name()).isEqualTo("Leg day");
        assertThat(response.exercises()).extracting("name").containsExactly("Bench Press", "Squat");
        assertThat(response.exercises().get(0).sets()).isEqualTo(3);
        assertThat(response.exercises().get(0).targetReps()).isEqualTo(6);
        assertThat(response.exercises().get(1).sets()).isEqualTo(1);
    }

    @Test
    void fromWorkoutPrefersTheGivenNameThenTheDate() {
        Workout workout = new Workout(user, LocalDate.of(2026, 10, 18), null);
        workout.addSet(new WorkoutSet(squat, 1, 5, new BigDecimal("100"), null));
        when(workoutRepository.findDetailByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(workout));
        when(routineRepository.save(any(Routine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(routineService.createFromWorkout(5L, new FromWorkoutRequest(" Mine ")).name()).isEqualTo("Mine");
        assertThat(routineService.createFromWorkout(5L, new FromWorkoutRequest(null)).name()).isEqualTo("2026-10-18");
    }

    @Test
    void fromAnEmptyWorkoutIsRejected() {
        when(workoutRepository.findDetailByIdAndUserId(5L, USER_ID))
                .thenReturn(Optional.of(new Workout(user, LocalDate.of(2026, 10, 18), null)));

        assertThatThrownBy(() -> routineService.createFromWorkout(5L, null))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void mostCommonRepsTieGoesToTheHigher() {
        assertThat(RoutineService.mostCommonReps(List.of(
                new WorkoutSet(squat, 1, 5, BigDecimal.TEN, null),
                new WorkoutSet(squat, 2, 8, BigDecimal.TEN, null)))).isEqualTo(8);
    }

    @Test
    void startUsesTodayAndTheRoutineName() {
        Routine routine = new Routine(user, "Legs");
        when(routineRepository.findDetailByIdAndUserId(3L, USER_ID)).thenReturn(Optional.of(routine));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var workout = routineService.startRoutine(3L, null);

        assertThat(workout.workoutDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(workout.notes()).isEqualTo("Legs");
    }

    @Test
    void planOfAWorkoutWithoutRoutineIsNotFound() {
        when(workoutRepository.findDetailByIdAndUserId(5L, USER_ID))
                .thenReturn(Optional.of(new Workout(user, LocalDate.of(2026, 10, 18), null)));

        assertThatThrownBy(() -> routineService.plan(5L)).isInstanceOf(ResourceNotFoundException.class);
    }
}

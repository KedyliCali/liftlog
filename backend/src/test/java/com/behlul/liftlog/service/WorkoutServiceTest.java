package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.SetRequest;
import com.behlul.liftlog.dto.SetResponse;
import com.behlul.liftlog.dto.WorkoutRequest;
import com.behlul.liftlog.entity.Exercise;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.entity.Workout;
import com.behlul.liftlog.entity.WorkoutSet;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.ExerciseRepository;
import com.behlul.liftlog.repository.SetFact;
import com.behlul.liftlog.repository.StatsRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.repository.WorkoutRepository;
import com.behlul.liftlog.repository.WorkoutSetRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    private static final long USER_ID = 7L;

    @Mock private WorkoutRepository workoutRepository;
    @Mock private WorkoutSetRepository setRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private UserRepository userRepository;
    @Mock private StatsRepository statsRepository;
    @Mock private CurrentUserProvider currentUser;

    @InjectMocks private WorkoutService workoutService;

    private final User user = new User("lifter@example.com", "hash", "Lifter");
    private final Exercise squat = new Exercise("Squat", "Legs", null);

    @BeforeEach
    void setUp() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
    }

    @Test
    void addSetGivesFirstSetOrderOne() {
        Workout workout = new Workout(user, LocalDate.of(2026, 10, 17), null);
        when(workoutRepository.findDetailByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(workout));
        when(exerciseRepository.findVisibleById(3L, USER_ID)).thenReturn(Optional.of(squat));

        SetResponse response = workoutService.addSet(1L, setRequest(3L, 5, "140.00"));

        assertThat(response.setOrder()).isEqualTo(1);
        assertThat(response.exerciseName()).isEqualTo("Squat");
        assertThat(workout.getSets()).hasSize(1);
        verify(setRepository).saveAndFlush(any(WorkoutSet.class));
    }

    @Test
    void addSetContinuesAfterHighestExistingOrder() {
        Workout workout = new Workout(user, LocalDate.of(2026, 10, 17), null);
        workout.addSet(new WorkoutSet(squat, 1, 5, new BigDecimal("100"), null));
        workout.addSet(new WorkoutSet(squat, 4, 5, new BigDecimal("120"), null));
        when(workoutRepository.findDetailByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(workout));
        when(exerciseRepository.findVisibleById(3L, USER_ID)).thenReturn(Optional.of(squat));

        SetResponse response = workoutService.addSet(1L, setRequest(3L, 3, "130.00"));

        assertThat(response.setOrder()).isEqualTo(5);
    }

    @Test
    void addSetFlagsNewPersonalRecordOnlyWhenItBeatsThePreviousBest() {
        Workout workout = new Workout(user, LocalDate.of(2026, 10, 17), null);
        when(workoutRepository.findDetailByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(workout));
        when(exerciseRepository.findVisibleById(3L, USER_ID)).thenReturn(Optional.of(squat));
        // Previous best: 100 kg x 5 -> e1RM 116.67
        when(statsRepository.findFactsForExercise(USER_ID, squat.getId())).thenReturn(List.of(
                new SetFact(1L, squat.getId(), "Squat", LocalDate.of(2026, 10, 10), 5, new BigDecimal("100"))));

        SetResponse weaker = workoutService.addSet(1L, setRequest(3L, 5, "100"));
        SetResponse stronger = workoutService.addSet(1L, setRequest(3L, 3, "110"));

        assertThat(weaker.newPersonalRecord()).isFalse();
        assertThat(stronger.newPersonalRecord()).isTrue(); // 110 x 3 -> e1RM 121.00
    }

    @Test
    void firstEverSetOfAnExerciseIsNotAnnouncedAsRecord() {
        Workout workout = new Workout(user, LocalDate.of(2026, 10, 17), null);
        when(workoutRepository.findDetailByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(workout));
        when(exerciseRepository.findVisibleById(3L, USER_ID)).thenReturn(Optional.of(squat));

        assertThat(workoutService.addSet(1L, setRequest(3L, 1, "200")).newPersonalRecord()).isFalse();
    }

    @Test
    void addSetToForeignWorkoutIsNotFound() {
        when(workoutRepository.findDetailByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workoutService.addSet(99L, setRequest(3L, 5, "100")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Workout 99");
        verify(setRepository, never()).saveAndFlush(any());
    }

    @Test
    void addSetWithInvisibleExerciseIsNotFound() {
        Workout workout = new Workout(user, LocalDate.of(2026, 10, 17), null);
        when(workoutRepository.findDetailByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(workout));
        when(exerciseRepository.findVisibleById(42L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workoutService.addSet(1L, setRequest(42L, 5, "100")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Exercise 42");
        assertThat(workout.getSets()).isEmpty();
    }

    @Test
    void createWorkoutTrimsNotesAndTreatsBlankAsNull() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        var blank = workoutService.createWorkout(new WorkoutRequest(LocalDate.of(2026, 10, 17), "   "));
        var padded = workoutService.createWorkout(new WorkoutRequest(LocalDate.of(2026, 10, 18), "  Push day "));

        assertThat(blank.notes()).isNull();
        assertThat(padded.notes()).isEqualTo("Push day");
    }

    @Test
    void listWorkoutsClampsPageSize() {
        when(workoutRepository.findSummariesByUserId(eq(USER_ID), any(Pageable.class))).thenReturn(Page.empty());

        workoutService.listWorkouts(-3, 5000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(workoutRepository).findSummariesByUserId(eq(USER_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(WorkoutService.MAX_PAGE_SIZE);
    }

    @Test
    void deleteSetRemovesItFromItsWorkout() {
        Workout workout = new Workout(user, LocalDate.of(2026, 10, 17), null);
        WorkoutSet set = new WorkoutSet(squat, 1, 5, new BigDecimal("100"), null);
        workout.addSet(set);
        when(setRepository.findOwnedById(10L, USER_ID)).thenReturn(Optional.of(set));

        workoutService.deleteSet(10L);

        assertThat(workout.getSets()).isEmpty();
    }

    @Test
    void deleteForeignWorkoutIsNotFound() {
        when(workoutRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workoutService.deleteWorkout(5L)).isInstanceOf(ResourceNotFoundException.class);
        verify(workoutRepository, never()).delete(any());
    }

    private static SetRequest setRequest(Long exerciseId, int reps, String weight) {
        return new SetRequest(exerciseId, reps, new BigDecimal(weight), null);
    }
}

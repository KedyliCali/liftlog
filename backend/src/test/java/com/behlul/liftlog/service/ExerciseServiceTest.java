package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.CreateExerciseRequest;
import com.behlul.liftlog.dto.ExerciseResponse;
import com.behlul.liftlog.entity.Exercise;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.exception.ConflictException;
import com.behlul.liftlog.repository.ExerciseRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    private static final long USER_ID = 7L;

    @Mock private ExerciseRepository exerciseRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUser;

    @InjectMocks private ExerciseService exerciseService;

    private final User user = new User("lifter@example.com", "hash", "Lifter");

    @BeforeEach
    void setUp() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
    }

    @Test
    void createRejectsDuplicateNameCaseInsensitively() {
        when(exerciseRepository.existsVisibleByName("squat", USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> exerciseService.createExercise(new CreateExerciseRequest("  squat ", null, null)))
                .isInstanceOf(ConflictException.class);
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void createTrimsNameAndMarksExerciseAsCustom() {
        when(exerciseRepository.existsVisibleByName("Hip Thrust", USER_ID)).thenReturn(false);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        ExerciseResponse response = exerciseService.createExercise(new CreateExerciseRequest(" Hip Thrust ", " ", null));

        assertThat(response.name()).isEqualTo("Hip Thrust");
        assertThat(response.muscleGroup()).isNull();
        assertThat(response.custom()).isTrue();
    }

    @Test
    void listMarksBuiltInExercisesAsNotCustom() {
        when(exerciseRepository.findVisibleTo(USER_ID)).thenReturn(List.of(
                new Exercise("Bench Press", "Chest", null),
                new Exercise("Hip Thrust", "Legs", user)));

        List<ExerciseResponse> exercises = exerciseService.listExercises();

        assertThat(exercises).extracting(ExerciseResponse::custom).containsExactly(false, true);
    }
}

package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.CreateExerciseRequest;
import com.behlul.liftlog.dto.ExerciseResponse;
import com.behlul.liftlog.dto.ExerciseResponse.MuscleRoleResponse;
import com.behlul.liftlog.entity.Exercise;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.exception.ConflictException;
import com.behlul.liftlog.repository.ExerciseRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;

    public ExerciseService(ExerciseRepository exerciseRepository, UserRepository userRepository,
                           CurrentUserProvider currentUser) {
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ExerciseResponse> listExercises() {
        List<Exercise> exercises = exerciseRepository.findVisibleTo(currentUser.currentUserId());
        Map<Long, List<MuscleRoleResponse>> muscles = musclesOf(exercises);
        return exercises.stream()
                .map(exercise -> ExerciseResponse.from(exercise, muscles.getOrDefault(exercise.getId(), List.of())))
                .toList();
    }

    @Transactional
    public ExerciseResponse createExercise(CreateExerciseRequest request) {
        Long userId = currentUser.currentUserId();
        String name = request.name().trim();
        if (exerciseRepository.existsVisibleByName(name, userId)) {
            throw new ConflictException("Exercise '" + name + "' already exists");
        }
        User owner = userRepository.getReferenceById(userId);
        String muscleGroup = request.muscleGroup() == null || request.muscleGroup().isBlank()
                ? null : request.muscleGroup().trim();
        Exercise saved = exerciseRepository.save(new Exercise(name, muscleGroup, Boolean.TRUE.equals(request.bodyweight()), owner));
        // A new custom exercise has no stored muscles, so they come from its muscle group
        List<MuscleRoleResponse> muscles = MuscleMap.resolve(List.of(), saved.getMuscleGroup()).stream()
                .map(ExerciseService::toResponse)
                .toList();
        return ExerciseResponse.from(saved, muscles);
    }

    private Map<Long, List<MuscleRoleResponse>> musclesOf(List<Exercise> exercises) {
        if (exercises.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> muscleGroupById = new HashMap<>();
        exercises.forEach(exercise -> muscleGroupById.put(exercise.getId(), exercise.getMuscleGroup()));
        Map<Long, List<MuscleRoleResponse>> result = new HashMap<>();
        MuscleMap.resolveAll(muscleGroupById, exerciseRepository.findMuscles(muscleGroupById.keySet()))
                .forEach((id, roles) -> result.put(id, roles.stream().map(ExerciseService::toResponse).toList()));
        return result;
    }

    private static MuscleRoleResponse toResponse(MuscleMap.MuscleRole entry) {
        return new MuscleRoleResponse(entry.muscle().name(), entry.role().name());
    }
}

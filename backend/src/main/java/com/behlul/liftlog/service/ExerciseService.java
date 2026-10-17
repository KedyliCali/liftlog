package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.CreateExerciseRequest;
import com.behlul.liftlog.dto.ExerciseResponse;
import com.behlul.liftlog.entity.Exercise;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.exception.ConflictException;
import com.behlul.liftlog.repository.ExerciseRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        return exerciseRepository.findVisibleTo(currentUser.currentUserId()).stream()
                .map(ExerciseResponse::from)
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
        Exercise saved = exerciseRepository.save(new Exercise(name, muscleGroup, owner));
        return ExerciseResponse.from(saved);
    }
}

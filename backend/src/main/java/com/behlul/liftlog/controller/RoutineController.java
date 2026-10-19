package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.RoutineDtos.FromWorkoutRequest;
import com.behlul.liftlog.dto.RoutineDtos.RoutineRequest;
import com.behlul.liftlog.dto.RoutineDtos.RoutineResponse;
import com.behlul.liftlog.dto.RoutineDtos.StartRoutineRequest;
import com.behlul.liftlog.dto.WorkoutDetailResponse;
import com.behlul.liftlog.service.RoutineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/routines")
public class RoutineController {

    private final RoutineService routineService;

    public RoutineController(RoutineService routineService) {
        this.routineService = routineService;
    }

    @GetMapping
    public List<RoutineResponse> list() {
        return routineService.listRoutines();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoutineResponse create(@Valid @RequestBody RoutineRequest request) {
        return routineService.createRoutine(request);
    }

    @PutMapping("/{id}")
    public RoutineResponse update(@PathVariable Long id, @Valid @RequestBody RoutineRequest request) {
        return routineService.updateRoutine(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        routineService.deleteRoutine(id);
    }

    /** The body is optional: without a name the workout's note (or its date) becomes the name. */
    @PostMapping("/from-workout/{workoutId}")
    @ResponseStatus(HttpStatus.CREATED)
    public RoutineResponse fromWorkout(@PathVariable Long workoutId,
                                       @Valid @RequestBody(required = false) FromWorkoutRequest request) {
        return routineService.createFromWorkout(workoutId, request);
    }

    /** The body is optional: without a date the workout is for today (UTC). */
    @PostMapping("/{id}/start")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutDetailResponse start(@PathVariable Long id,
                                       @RequestBody(required = false) StartRoutineRequest request) {
        return routineService.startRoutine(id, request);
    }
}

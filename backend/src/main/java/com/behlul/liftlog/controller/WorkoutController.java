package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.PageResponse;
import com.behlul.liftlog.dto.RoutineDtos.PlanItemResponse;
import com.behlul.liftlog.dto.SetRequest;
import com.behlul.liftlog.dto.SetResponse;
import com.behlul.liftlog.dto.WorkoutDetailResponse;
import com.behlul.liftlog.dto.WorkoutRecapResponse;
import com.behlul.liftlog.dto.WorkoutRequest;
import com.behlul.liftlog.dto.WorkoutSummaryResponse;
import com.behlul.liftlog.service.RoutineService;
import com.behlul.liftlog.service.WorkoutRecapService;
import com.behlul.liftlog.service.WorkoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workouts")
public class WorkoutController {

    private final WorkoutService workoutService;
    private final RoutineService routineService;
    private final WorkoutRecapService recapService;

    public WorkoutController(WorkoutService workoutService, RoutineService routineService,
                             WorkoutRecapService recapService) {
        this.workoutService = workoutService;
        this.routineService = routineService;
        this.recapService = recapService;
    }

    @GetMapping
    public PageResponse<WorkoutSummaryResponse> list(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return workoutService.listWorkouts(page, size);
    }

    @GetMapping("/{id}")
    public WorkoutDetailResponse get(@PathVariable Long id) {
        return workoutService.getWorkout(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutDetailResponse create(@Valid @RequestBody WorkoutRequest request) {
        return workoutService.createWorkout(request);
    }

    @PutMapping("/{id}")
    public WorkoutDetailResponse update(@PathVariable Long id, @Valid @RequestBody WorkoutRequest request) {
        return workoutService.updateWorkout(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        workoutService.deleteWorkout(id);
    }

    /** Planned sets of a workout started from a routine; 404 for a workout without one. */
    @GetMapping("/{id}/plan")
    public List<PlanItemResponse> plan(@PathVariable Long id) {
        return routineService.plan(id);
    }

    /** End-of-workout summary: volume, records hit, comparison with the previous session and the streak. */
    @GetMapping("/{id}/summary")
    public WorkoutRecapResponse summary(@PathVariable Long id) {
        return recapService.summary(id);
    }

    @PostMapping("/{id}/sets")
    @ResponseStatus(HttpStatus.CREATED)
    public SetResponse addSet(@PathVariable Long id, @Valid @RequestBody SetRequest request) {
        return workoutService.addSet(id, request);
    }
}

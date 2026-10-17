package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.SetRequest;
import com.behlul.liftlog.dto.SetResponse;
import com.behlul.liftlog.service.WorkoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sets")
public class SetController {

    private final WorkoutService workoutService;

    public SetController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @PutMapping("/{id}")
    public SetResponse update(@PathVariable Long id, @Valid @RequestBody SetRequest request) {
        return workoutService.updateSet(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        workoutService.deleteSet(id);
    }
}

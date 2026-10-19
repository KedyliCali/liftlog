package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.GroupTrainingDtos.GroupTrainingResponse;
import com.behlul.liftlog.service.GroupTrainingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** The caller's friend group (the same group as League) compared on training. */
@RestController
@RequestMapping("/api/v1/group")
public class GroupTrainingController {

    private final GroupTrainingService trainingService;

    public GroupTrainingController(GroupTrainingService trainingService) {
        this.trainingService = trainingService;
    }

    /**
     * days must be 7 or 30; today (YYYY-MM-DD) is the phone's date, the server's date when absent.
     * 204 when the caller is in no group, like GET /lol/group.
     */
    @GetMapping("/training")
    public ResponseEntity<GroupTrainingResponse> training(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return trainingService.training(days, today).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}

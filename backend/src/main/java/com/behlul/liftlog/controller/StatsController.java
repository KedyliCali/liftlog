package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.StatsDtos.MuscleStatsResponse;
import com.behlul.liftlog.dto.StatsDtos.PersonalRecordResponse;
import com.behlul.liftlog.dto.StatsDtos.ProgressPointResponse;
import com.behlul.liftlog.dto.StatsDtos.StreakResponse;
import com.behlul.liftlog.dto.StatsDtos.WeeklyVolumeResponse;
import com.behlul.liftlog.service.StatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/prs")
    public List<PersonalRecordResponse> personalRecords() {
        return statsService.personalRecords();
    }

    @GetMapping("/progress")
    public List<ProgressPointResponse> progress(@RequestParam Long exerciseId) {
        return statsService.progress(exerciseId);
    }

    @GetMapping("/streak")
    public StreakResponse streak() {
        return statsService.streak();
    }

    /** days must be 7 or 30; today (YYYY-MM-DD) is the phone's date, the server's date when absent. */
    @GetMapping("/muscles")
    public MuscleStatsResponse muscles(@RequestParam(defaultValue = "7") int days,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return statsService.muscles(days, today);
    }

    @GetMapping("/volume")
    public List<WeeklyVolumeResponse> weeklyVolume(@RequestParam(defaultValue = "8") int weeks) {
        return statsService.weeklyVolume(weeks);
    }
}

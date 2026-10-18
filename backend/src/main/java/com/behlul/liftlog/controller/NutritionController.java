package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.NutritionDtos.FoodEntryRequest;
import com.behlul.liftlog.dto.NutritionDtos.FoodEntryResponse;
import com.behlul.liftlog.dto.NutritionDtos.FrequentFoodResponse;
import com.behlul.liftlog.dto.NutritionDtos.NutritionDayResponse;
import com.behlul.liftlog.dto.NutritionDtos.NutritionGoals;
import com.behlul.liftlog.service.NutritionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/nutrition")
public class NutritionController {

    private final NutritionService nutritionService;

    public NutritionController(NutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    /** The client sends its own calendar date, so "today" follows the phone's time zone rather than the server's. */
    @GetMapping("/days/{date}")
    public NutritionDayResponse day(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return nutritionService.getDay(date);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public FoodEntryResponse addEntry(@Valid @RequestBody FoodEntryRequest request) {
        return nutritionService.addEntry(request);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@PathVariable Long id) {
        nutritionService.deleteEntry(id);
    }

    @GetMapping("/frequent")
    public List<FrequentFoodResponse> frequent() {
        return nutritionService.frequentFoods();
    }

    @PutMapping("/goals")
    public NutritionGoals updateGoals(@Valid @RequestBody NutritionGoals goals) {
        return nutritionService.updateGoals(goals);
    }
}

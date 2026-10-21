package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.NutritionDtos.FoodResponse;
import com.behlul.liftlog.service.NutritionService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * The whole catalog in one response (about a thousand rows, a few dozen KB gzipped): the client searches it
 * locally, so typing gives instant results even on a slow connection.
 */
@RestController
@RequestMapping("/api/v1/foods")
public class FoodController {

    private final NutritionService nutritionService;

    public FoodController(NutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @GetMapping
    public ResponseEntity<List<FoodResponse>> list() {
        // Only changes with a migration, so a day of private caching is safe
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(nutritionService.foodCatalog());
    }
}

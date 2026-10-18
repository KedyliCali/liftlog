package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.FoodEntry;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class NutritionDtos {

    private NutritionDtos() {
    }

    /** Limits mirror the database CHECK constraints. */
    public record FoodEntryRequest(
            @NotNull LocalDate entryDate,
            @NotBlank @Size(max = 100) String name,
            @NotNull @Min(0) @Max(10000) Integer caloriesKcal,
            @NotNull @DecimalMin("0.0") @DecimalMax("1000.0") @Digits(integer = 4, fraction = 1) BigDecimal proteinG
    ) {
    }

    public record FoodEntryResponse(Long id, LocalDate entryDate, String name, int caloriesKcal, BigDecimal proteinG) {

        public static FoodEntryResponse from(FoodEntry entry) {
            return new FoodEntryResponse(entry.getId(), entry.getEntryDate(), entry.getName(),
                    entry.getCaloriesKcal(), entry.getProteinG());
        }
    }

    /** Null clears a goal. */
    public record NutritionGoals(
            @Min(500) @Max(10000) Integer calorieGoalKcal,
            @Min(10) @Max(500) Integer proteinGoalG
    ) {
    }

    /** Everything the day screen needs in one request: entries, their totals and the goals to compare with. */
    public record NutritionDayResponse(LocalDate date, List<FoodEntryResponse> entries, int totalCaloriesKcal,
                                       BigDecimal totalProteinG, NutritionGoals goals) {
    }

    public record FrequentFoodResponse(String name, int caloriesKcal, BigDecimal proteinG, long timesEaten) {
    }
}

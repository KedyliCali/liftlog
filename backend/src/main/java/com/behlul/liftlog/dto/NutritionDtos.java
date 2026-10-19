package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.Food;
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
            @NotNull @DecimalMin("0.0") @DecimalMax("1000.0") @Digits(integer = 4, fraction = 1) BigDecimal proteinG,
            /* Optional: older clients and quick manual entries may not know them */
            @DecimalMin("0.0") @DecimalMax("1000.0") @Digits(integer = 4, fraction = 1) BigDecimal carbsG,
            @DecimalMin("0.0") @DecimalMax("1000.0") @Digits(integer = 4, fraction = 1) BigDecimal fatG,
            /* Only sent when the user chose an amount (catalog or scanned food); hand-typed entries leave it out */
            @DecimalMin("0.1") @DecimalMax("50000.0") @Digits(integer = 5, fraction = 1) BigDecimal grams
    ) {
    }

    /** carbsG and fatG are null when the entry was logged without them; grams when the portion is unknown. */
    public record FoodEntryResponse(Long id, LocalDate entryDate, String name, int caloriesKcal, BigDecimal proteinG,
                                    BigDecimal carbsG, BigDecimal fatG, BigDecimal grams) {

        public static FoodEntryResponse from(FoodEntry entry) {
            return new FoodEntryResponse(entry.getId(), entry.getEntryDate(), entry.getName(),
                    entry.getCaloriesKcal(), entry.getProteinG(), entry.getCarbsG(), entry.getFatG(),
                    entry.getGrams());
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
                                       BigDecimal totalProteinG, BigDecimal totalCarbsG, BigDecimal totalFatG,
                                       NutritionGoals goals) {
    }

    /** Catalog food with both languages; the client shows the one matching the phone's language. */
    public record FoodResponse(Long id, String category, String nameTr, String nameEn, BigDecimal kcalPer100g,
                               BigDecimal proteinPer100g, BigDecimal carbsPer100g, BigDecimal fatPer100g,
                               BigDecimal servingG, String servingTr, String servingEn) {

        public static FoodResponse from(Food food) {
            return new FoodResponse(food.getId(), food.getCategory(), food.getNameTr(), food.getNameEn(),
                    food.getKcalPer100g(), food.getProteinPer100g(), food.getCarbsPer100g(), food.getFatPer100g(),
                    food.getServingG(), food.getServingTr(), food.getServingEn());
        }
    }

    public record FrequentFoodResponse(String name, int caloriesKcal, BigDecimal proteinG, BigDecimal carbsG,
                                       BigDecimal fatG, long timesEaten) {
    }
}

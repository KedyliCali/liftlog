package com.behlul.liftlog.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One food diary entry for the CSV export; grams, carbsG and fatG are null when they were not logged
 * (grams is also null for every entry made before portions were stored).
 */
public record ExportedFoodEntry(LocalDate entryDate, String name, BigDecimal grams, int caloriesKcal,
                                BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG) {
}

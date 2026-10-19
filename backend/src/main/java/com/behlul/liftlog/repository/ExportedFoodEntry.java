package com.behlul.liftlog.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One food diary entry for the CSV export; carbsG and fatG are null when they were not logged. */
public record ExportedFoodEntry(LocalDate entryDate, String name, int caloriesKcal, BigDecimal proteinG,
                                BigDecimal carbsG, BigDecimal fatG) {
}

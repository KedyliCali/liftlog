package com.behlul.liftlog.repository;

import java.math.BigDecimal;

/** One logged food as the quick-add list shows it, with how often it was eaten recently. */
public record FrequentFood(String name, int caloriesKcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG,
                           long timesEaten) {
}

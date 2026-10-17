package com.behlul.liftlog.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Estimated one-rep max with the Epley formula: weight × (1 + reps / 30).
 * A single rep is already a true max, so it is returned unchanged.
 */
public final class OneRepMax {

    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);

    private OneRepMax() {
    }

    public static BigDecimal estimate(BigDecimal weightKg, int reps) {
        if (reps < 1) {
            throw new IllegalArgumentException("reps must be at least 1");
        }
        if (reps == 1) {
            return weightKg.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(BigDecimal.valueOf(reps).divide(THIRTY, 10, RoundingMode.HALF_UP));
        return weightKg.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}

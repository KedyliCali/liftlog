package com.behlul.liftlog.repository;

import java.math.BigDecimal;

/** A user's set count and volume (reps × effective weight) in a date window. */
public record UserSetTotals(Long userId, long sets, BigDecimal volumeKg) {
}

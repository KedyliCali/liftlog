package com.behlul.liftlog.dto;

import com.behlul.liftlog.entity.WeightEntry;
import com.behlul.liftlog.service.WeightTrend;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class WeightDtos {

    private WeightDtos() {
    }

    /** Limits mirror the weight_entries CHECK constraint. */
    public record WeightEntryRequest(
            @NotNull @DecimalMin("20.0") @DecimalMax("400.0") @Digits(integer = 3, fraction = 2) BigDecimal weightKg
    ) {
    }

    public record WeightEntryResponse(LocalDate date, BigDecimal weightKg) {

        public static WeightEntryResponse from(WeightEntry entry) {
            return new WeightEntryResponse(entry.getEntryDate(), entry.getWeightKg());
        }

        static WeightEntryResponse from(WeightTrend.Point point) {
            return point == null ? null : new WeightEntryResponse(point.date(), point.weightKg());
        }
    }

    /**
     * @param entries   oldest first, only those inside the requested window
     * @param latest    the newest entry ever, even when it is older than the window; null without entries
     * @param change7d  kg since about a week before {@code latest}; null when there is no such entry
     * @param change30d kg since about a month before {@code latest}; null when there is no such entry
     * @param average7d mean of the 7 days ending on {@code latest}
     */
    public record WeightHistoryResponse(List<WeightEntryResponse> entries, WeightEntryResponse latest,
                                        BigDecimal change7d, BigDecimal change30d, BigDecimal average7d) {

        public static WeightHistoryResponse of(List<WeightEntryResponse> entries, WeightTrend trend) {
            return new WeightHistoryResponse(entries, WeightEntryResponse.from(trend.latest()), trend.change7d(),
                    trend.change30d(), trend.average7d());
        }
    }
}

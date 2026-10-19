package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One weigh-in per user per day; a second one on the same day overwrites the weight. */
@Entity
@Table(name = "weight_entries")
public class WeightEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A plain id rather than a User relation: entries are only ever read for the signed-in user
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "entry_date", nullable = false, updatable = false)
    private LocalDate entryDate;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WeightEntry() {
    }

    public WeightEntry(Long userId, LocalDate entryDate, BigDecimal weightKg) {
        this.userId = userId;
        this.entryDate = entryDate;
        this.weightKg = weightKg;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }
}

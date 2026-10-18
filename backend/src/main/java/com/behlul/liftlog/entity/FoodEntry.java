package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "food_entries")
public class FoodEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "calories_kcal", nullable = false)
    private int caloriesKcal;

    @Column(name = "protein_g", nullable = false, precision = 5, scale = 1)
    private BigDecimal proteinG;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected FoodEntry() {
    }

    public FoodEntry(User user, LocalDate entryDate, String name, int caloriesKcal, BigDecimal proteinG) {
        this.user = user;
        this.entryDate = entryDate;
        this.name = name;
        this.caloriesKcal = caloriesKcal;
        this.proteinG = proteinG;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public String getName() {
        return name;
    }

    public int getCaloriesKcal() {
        return caloriesKcal;
    }

    public BigDecimal getProteinG() {
        return proteinG;
    }
}

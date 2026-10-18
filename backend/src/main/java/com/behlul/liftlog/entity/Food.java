package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Read-only catalog row, seeded by migration V7. */
@Entity
@Table(name = "foods")
public class Food {

    @Id
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(name = "name_tr", nullable = false)
    private String nameTr;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "kcal_per_100g", nullable = false)
    private BigDecimal kcalPer100g;

    @Column(name = "protein_per_100g", nullable = false)
    private BigDecimal proteinPer100g;

    @Column(name = "serving_g", nullable = false)
    private BigDecimal servingG;

    @Column(name = "serving_tr", nullable = false)
    private String servingTr;

    @Column(name = "serving_en", nullable = false)
    private String servingEn;

    protected Food() {
    }

    public Long getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getNameTr() {
        return nameTr;
    }

    public String getNameEn() {
        return nameEn;
    }

    public BigDecimal getKcalPer100g() {
        return kcalPer100g;
    }

    public BigDecimal getProteinPer100g() {
        return proteinPer100g;
    }

    public BigDecimal getServingG() {
        return servingG;
    }

    public String getServingTr() {
        return servingTr;
    }

    public String getServingEn() {
        return servingEn;
    }
}

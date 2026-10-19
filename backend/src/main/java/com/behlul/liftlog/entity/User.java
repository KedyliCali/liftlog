package com.behlul.liftlog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "height_cm", precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "body_weight_kg", precision = 5, scale = 2)
    private BigDecimal bodyWeightKg;

    /** Raised by every password change; tokens carrying an older value are rejected (TokenVersionValidator). */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "calorie_goal_kcal")
    private Integer calorieGoalKcal;

    @Column(name = "protein_goal_g")
    private Integer proteinGoalG;

    protected User() {
    }

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Stores a new password hash and invalidates every token issued before it. This is the only way to
     * change a password, so no future feature can change one and forget to sign other devices out.
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.tokenVersion++;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getBodyWeightKg() {
        return bodyWeightKg;
    }

    public void setBodyWeightKg(BigDecimal bodyWeightKg) {
        this.bodyWeightKg = bodyWeightKg;
    }

    public Integer getCalorieGoalKcal() {
        return calorieGoalKcal;
    }

    public Integer getProteinGoalG() {
        return proteinGoalG;
    }

    public void setNutritionGoals(Integer calorieGoalKcal, Integer proteinGoalG) {
        this.calorieGoalKcal = calorieGoalKcal;
        this.proteinGoalG = proteinGoalG;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

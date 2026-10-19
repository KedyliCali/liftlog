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

@Entity
@Table(name = "exercises")
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "muscle_group", length = 50)
    private String muscleGroup;

    /** True for calisthenics: logged weight is the added load and the user's body weight counts too. */
    @Column(nullable = false)
    private boolean bodyweight;

    /** BARBELL, DUMBBELL, MACHINE, CABLE or BODYWEIGHT for built-ins; null for custom exercises. */
    private String equipment;

    /** Owner of a custom exercise; {@code null} for built-in exercises. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    protected Exercise() {
    }

    public Exercise(String name, String muscleGroup, User owner) {
        this(name, muscleGroup, false, owner);
    }

    public Exercise(String name, String muscleGroup, boolean bodyweight, User owner) {
        this.name = name;
        this.muscleGroup = muscleGroup;
        this.bodyweight = bodyweight;
        this.owner = owner;
    }

    public String getEquipment() {
        return equipment;
    }

    public boolean isBodyweight() {
        return bodyweight;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getMuscleGroup() {
        return muscleGroup;
    }

    public User getOwner() {
        return owner;
    }

    public boolean isBuiltIn() {
        return owner == null;
    }
}

package com.behlul.liftlog.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workouts")
public class Workout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "workout_date", nullable = false)
    private LocalDate workoutDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** The routine this workout was started from; null for a free workout or once the routine is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    private Routine routine;

    @OneToMany(mappedBy = "workout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setOrder ASC")
    private List<WorkoutSet> sets = new ArrayList<>();

    protected Workout() {
    }

    public Workout(User user, LocalDate workoutDate, String notes) {
        this.user = user;
        this.workoutDate = workoutDate;
        this.notes = notes;
    }

    public Workout(User user, LocalDate workoutDate, String notes, Routine routine) {
        this(user, workoutDate, notes);
        this.routine = routine;
    }

    public void addSet(WorkoutSet set) {
        sets.add(set);
        set.setWorkout(this);
    }

    public void removeSet(WorkoutSet set) {
        sets.remove(set);
        set.setWorkout(null);
    }

    public int nextSetOrder() {
        return sets.stream().mapToInt(WorkoutSet::getSetOrder).max().orElse(0) + 1;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getWorkoutDate() {
        return workoutDate;
    }

    public void setWorkoutDate(LocalDate workoutDate) {
        this.workoutDate = workoutDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Routine getRoutine() {
        return routine;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<WorkoutSet> getSets() {
        return sets;
    }
}

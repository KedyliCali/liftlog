package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.WorkoutSet;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** Read-only statistics queries; extends the marker Repository so it exposes no save/delete methods. */
public interface StatsRepository extends Repository<WorkoutSet, Long> {

    @Query("""
            select new com.behlul.liftlog.repository.SetFact(
                s.id, e.id, e.name, w.workoutDate, s.reps, s.weightKg)
            from WorkoutSet s join s.workout w join s.exercise e
            where w.user.id = :userId
            order by w.workoutDate, s.setOrder
            """)
    List<SetFact> findAllFacts(@Param("userId") Long userId);

    @Query("""
            select new com.behlul.liftlog.repository.SetFact(
                s.id, e.id, e.name, w.workoutDate, s.reps, s.weightKg)
            from WorkoutSet s join s.workout w join s.exercise e
            where w.user.id = :userId and e.id = :exerciseId
            order by w.workoutDate, s.setOrder
            """)
    List<SetFact> findFactsForExercise(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    /**
     * Weekly volume computed in PostgreSQL; date_trunc('week') starts weeks on Monday (ISO 8601).
     * Returns only weeks that have sets; the service fills the gaps with zeros.
     */
    @Query(value = """
            select cast(date_trunc('week', w.workout_date) as date) as week_start,
                   sum(s.reps * s.weight_kg)                        as volume_kg,
                   count(s.id)                                      as set_count
            from workout_sets s
            join workouts w on w.id = s.workout_id
            where w.user_id = :userId and w.workout_date >= :fromDate
            group by week_start
            order by week_start
            """, nativeQuery = true)
    List<WeeklyVolumeRow> findWeeklyVolume(@Param("userId") Long userId, @Param("fromDate") LocalDate fromDate);

    interface WeeklyVolumeRow {
        LocalDate getWeekStart();

        java.math.BigDecimal getVolumeKg();

        long getSetCount();
    }
}

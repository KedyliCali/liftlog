package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.WorkoutSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, Long> {

    @Query("""
            select s from WorkoutSet s
            join fetch s.workout w
            join fetch s.exercise
            where s.id = :id and w.user.id = :userId
            """)
    Optional<WorkoutSet> findOwnedById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * For each given exercise, the sets of its "previous session": the user's most recent workout strictly
     * before the current one (an earlier date, or the same date and a smaller id, i.e. created before it) that
     * contains the exercise. One query for the whole plan; DISTINCT ON picks the newest workout per exercise.
     */
    @Query(value = """
            with previous as (
                select distinct on (s.exercise_id) s.exercise_id, s.workout_id
                from workout_sets s
                join workouts w on w.id = s.workout_id
                where w.user_id = :userId
                  and (w.workout_date < :workoutDate or (w.workout_date = :workoutDate and w.id < :workoutId))
                  and s.exercise_id in (:exerciseIds)
                order by s.exercise_id, w.workout_date desc, w.id desc
            )
            select s.exercise_id as exercise_id, s.reps as reps, s.weight_kg as weight_kg
            from workout_sets s
            join previous p on p.workout_id = s.workout_id and p.exercise_id = s.exercise_id
            order by s.exercise_id, s.set_order
            """, nativeQuery = true)
    List<PreviousSetRow> findPreviousSessionSets(@Param("userId") Long userId, @Param("workoutId") Long workoutId,
                                                 @Param("workoutDate") LocalDate workoutDate,
                                                 @Param("exerciseIds") Collection<Long> exerciseIds);

    interface PreviousSetRow {
        Long getExerciseId();

        int getReps();

        BigDecimal getWeightKg();
    }
}

package com.behlul.liftlog.repository;

import com.behlul.liftlog.dto.WorkoutSummaryResponse;
import com.behlul.liftlog.entity.Workout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    Optional<Workout> findByIdAndUserId(Long id, Long userId);

    /** Loads a workout with its sets and their exercises in one query to avoid N+1 selects. */
    @Query("""
            select distinct w from Workout w
            left join fetch w.sets s
            left join fetch s.exercise
            where w.id = :id and w.user.id = :userId
            """)
    Optional<Workout> findDetailByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** One row per workout with aggregates computed in the database, newest first. */
    @Query(value = """
            select new com.behlul.liftlog.dto.WorkoutSummaryResponse(
                w.id, w.workoutDate, w.notes,
                count(s.id),
                count(distinct s.exercise.id),
                coalesce(sum(s.reps * (s.weightKg
                    + case when e.bodyweight = true then coalesce(u.bodyWeightKg, 0) else 0 end)), 0)
            )
            from Workout w join w.user u left join w.sets s left join s.exercise e
            where u.id = :userId
            group by w.id, w.workoutDate, w.notes
            order by w.workoutDate desc, w.id desc
            """,
            countQuery = "select count(w) from Workout w where w.user.id = :userId")
    Page<WorkoutSummaryResponse> findSummariesByUserId(@Param("userId") Long userId, Pageable pageable);
}

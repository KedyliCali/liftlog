package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.WorkoutSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, Long> {

    @Query("""
            select s from WorkoutSet s
            join fetch s.workout w
            join fetch s.exercise
            where s.id = :id and w.user.id = :userId
            """)
    Optional<WorkoutSet> findOwnedById(@Param("id") Long id, @Param("userId") Long userId);
}

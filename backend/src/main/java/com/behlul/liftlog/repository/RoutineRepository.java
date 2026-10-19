package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.Routine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

    /**
     * A user's routines with their exercises in one query (no N+1), alphabetical. Hibernate 6+ removes the
     * duplicate roots of a fetch join itself, so no DISTINCT (PostgreSQL rejects it with this ORDER BY).
     */
    @Query("""
            select r from Routine r
            left join fetch r.exercises re
            left join fetch re.exercise
            where r.user.id = :userId
            order by lower(r.name), r.id
            """)
    List<Routine> findAllWithExercisesByUserId(@Param("userId") Long userId);

    /** Scoped to the owner, so another user's routine looks exactly like a missing one. */
    @Query("""
            select r from Routine r
            left join fetch r.exercises re
            left join fetch re.exercise
            where r.id = :id and r.user.id = :userId
            """)
    Optional<Routine> findDetailByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}

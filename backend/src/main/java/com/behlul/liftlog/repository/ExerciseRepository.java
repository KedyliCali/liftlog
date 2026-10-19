package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /** Built-in exercises plus the ones this user created. */
    @Query("""
            select e from Exercise e
            where e.owner is null or e.owner.id = :userId
            order by lower(e.name)
            """)
    List<Exercise> findVisibleTo(@Param("userId") Long userId);

    @Query("""
            select e from Exercise e
            where e.id = :id and (e.owner is null or e.owner.id = :userId)
            """)
    Optional<Exercise> findVisibleById(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            select e from Exercise e
            where e.id in :ids and (e.owner is null or e.owner.id = :userId)
            """)
    List<Exercise> findVisibleByIds(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    @Query("""
            select count(e) > 0 from Exercise e
            where lower(e.name) = lower(:name) and (e.owner is null or e.owner.id = :userId)
            """)
    boolean existsVisibleByName(@Param("name") String name, @Param("userId") Long userId);

    /** Stored muscles of the given exercises (built-ins only have rows; see V17). Ids must not be empty. */
    @Query(value = """
            select m.exercise_id as exerciseId, m.muscle as muscle, m.role as role
            from exercise_muscles m
            where m.exercise_id in (:ids)
            """, nativeQuery = true)
    List<ExerciseMuscleRow> findMuscles(@Param("ids") Collection<Long> ids);

    interface ExerciseMuscleRow {
        Long getExerciseId();

        /** One of the 16 map regions, or ADDUCTORS / ABDUCTORS. */
        String getMuscle();

        /** PRIMARY or SECONDARY. */
        String getRole();
    }
}

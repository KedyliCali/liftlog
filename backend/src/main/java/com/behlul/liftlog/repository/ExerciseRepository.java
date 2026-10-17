package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
            select count(e) > 0 from Exercise e
            where lower(e.name) = lower(:name) and (e.owner is null or e.owner.id = :userId)
            """)
    boolean existsVisibleByName(@Param("name") String name, @Param("userId") Long userId);
}

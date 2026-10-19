package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.WorkoutSet;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.stream.Stream;

import static org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.HibernateHints.HINT_READ_ONLY;

/**
 * Exports read a user's whole history, so rows come back as a Stream of small records instead of a List:
 * the PostgreSQL driver fetches them in batches (fetch size) while the CSV is being written, and the
 * records are not managed entities, so nothing piles up in the persistence context. A Stream needs an
 * open transaction and must be closed, which ExportService does.
 */
public interface ExportRepository extends Repository<WorkoutSet, Long> {

    @QueryHints({@QueryHint(name = HINT_FETCH_SIZE, value = "500"),
            @QueryHint(name = HINT_READ_ONLY, value = "true")})
    @Query("""
            select new com.behlul.liftlog.repository.ExportedSet(
                       w.workoutDate, w.notes, e.name, s.setOrder, s.weightKg, s.reps, s.rpe, e.bodyweight)
            from WorkoutSet s
            join s.workout w
            join s.exercise e
            where w.user.id = :userId
            order by w.workoutDate, w.id, s.setOrder
            """)
    Stream<ExportedSet> streamSets(@Param("userId") Long userId);

    @QueryHints({@QueryHint(name = HINT_FETCH_SIZE, value = "500"),
            @QueryHint(name = HINT_READ_ONLY, value = "true")})
    @Query("""
            select new com.behlul.liftlog.repository.ExportedFoodEntry(
                       f.entryDate, f.name, f.grams, f.caloriesKcal, f.proteinG, f.carbsG, f.fatG)
            from FoodEntry f
            where f.user.id = :userId
            order by f.entryDate, f.id
            """)
    Stream<ExportedFoodEntry> streamFoodEntries(@Param("userId") Long userId);
}

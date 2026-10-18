package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.FoodEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FoodEntryRepository extends JpaRepository<FoodEntry, Long> {

    List<FoodEntry> findByUserIdAndEntryDateOrderByIdAsc(Long userId, LocalDate entryDate);

    Optional<FoodEntry> findByIdAndUserId(Long id, Long userId);

    /**
     * Same name and values count as the same food. Ties go to the one eaten most recently, so a new
     * habit shows up before an old one.
     */
    @Query("""
            select new com.behlul.liftlog.repository.FrequentFood(f.name, f.caloriesKcal, f.proteinG, count(f))
            from FoodEntry f
            where f.user.id = :userId and f.entryDate >= :since
            group by f.name, f.caloriesKcal, f.proteinG
            order by count(f) desc, max(f.id) desc
            """)
    List<FrequentFood> findFrequent(@Param("userId") Long userId, @Param("since") LocalDate since, Pageable page);
}

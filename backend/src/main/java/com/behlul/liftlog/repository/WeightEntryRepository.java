package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.WeightEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, Long> {

    List<WeightEntry> findByUserIdAndEntryDateGreaterThanEqualOrderByEntryDateAsc(Long userId, LocalDate from);

    Optional<WeightEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);

    /** The current weight: the entry with the latest date (not the one written last). */
    Optional<WeightEntry> findFirstByUserIdOrderByEntryDateDesc(Long userId);
}

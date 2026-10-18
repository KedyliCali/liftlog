package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodRepository extends JpaRepository<Food, Long> {

    /** Seed order groups the catalog by category, which is how the picker lists it. */
    List<Food> findAllByOrderByIdAsc();
}

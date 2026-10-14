package com.crave.repository;

import com.crave.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Repository for Food entity database operations.
 */
public interface FoodRepository extends JpaRepository<Food, Long> {

    /**
     * Find all foods in a specific category (case-insensitive).
     */
    List<Food> findByCategoryIgnoreCase(String category);

    /**
     * Search foods by name, category, or description (case-insensitive partial match).
     */
    @Query("SELECT f FROM Food f WHERE " +
           "LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(f.category) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(f.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Food> searchFoods(@Param("query") String query);
}

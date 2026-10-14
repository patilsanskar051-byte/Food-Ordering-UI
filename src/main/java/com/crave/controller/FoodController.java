package com.crave.controller;

import com.crave.entity.Food;
import com.crave.service.FoodService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for food menu endpoints.
 */
@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    /**
     * GET /api/foods
     * GET /api/foods?category=Burgers
     * GET /api/foods?search=burger
     *
     * Returns all foods, or filtered by category or search query.
     */
    @GetMapping
    public List<Food> getFoods(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        return foodService.getFoods(category, search);
    }

    /**
     * GET /api/foods/{id}
     *
     * Returns a single food item, or 404 if not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Food> getFoodById(@PathVariable Long id) {
        return foodService.getFoodById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

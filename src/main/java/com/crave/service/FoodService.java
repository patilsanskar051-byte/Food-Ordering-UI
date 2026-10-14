package com.crave.service;

import com.crave.dto.MenuOverrideData;
import com.crave.entity.Food;
import com.crave.repository.FoodRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for food-related business logic.
 * Integrates database-backed food records with file-based price & availability overrides
 * and custom items from MenuFileService.
 */
@Service
public class FoodService {

    private final FoodRepository foodRepository;
    private final MenuFileService menuFileService;

    public FoodService(FoodRepository foodRepository, MenuFileService menuFileService) {
        this.foodRepository = foodRepository;
        this.menuFileService = menuFileService;
    }

    /**
     * Get all food items, merging database foods with file overrides and custom foods.
     * Optionally filtered by category or search query.
     */
    public List<Food> getFoods(String category, String search) {
        List<Food> allFoods = getAllMergedFoods();

        // 1. If search query is provided, filter by search query (name, category, description)
        if (search != null && !search.trim().isEmpty()) {
            String queryLower = search.trim().toLowerCase();
            return allFoods.stream()
                    .filter(f -> (f.getName() != null && f.getName().toLowerCase().contains(queryLower))
                            || (f.getCategory() != null && f.getCategory().toLowerCase().contains(queryLower))
                            || (f.getDescription() != null && f.getDescription().toLowerCase().contains(queryLower)))
                    .toList();
        }

        // 2. If category is provided and not "All", filter by category
        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("All")) {
            return allFoods.stream()
                    .filter(f -> f.getCategory() != null && f.getCategory().equalsIgnoreCase(category.trim()))
                    .toList();
        }

        return allFoods;
    }

    /**
     * Get a single food item by ID, with file overrides applied.
     */
    public Optional<Food> getFoodById(Long id) {
        if (id == null) return Optional.empty();

        // Check database first
        Optional<Food> foodOpt = foodRepository.findById(id);
        if (foodOpt.isPresent()) {
            Food food = copyFood(foodOpt.get());
            applyOverrides(food);
            return Optional.of(food);
        }

        // Check custom foods from file
        Optional<Food> customOpt = menuFileService.findCustomFoodById(id);
        if (customOpt.isPresent()) {
            Food food = copyFood(customOpt.get());
            applyOverrides(food);
            return Optional.of(food);
        }

        return Optional.empty();
    }

    /**
     * Finds the maximum ID among database foods and custom foods.
     */
    public long getMaxFoodId() {
        long maxId = 9L;
        List<Food> dbFoods = foodRepository.findAll();
        for (Food f : dbFoods) {
            if (f.getId() != null && f.getId() > maxId) {
                maxId = f.getId();
            }
        }
        for (Food f : menuFileService.getCustomFoods()) {
            if (f.getId() != null && f.getId() > maxId) {
                maxId = f.getId();
            }
        }
        return maxId;
    }

    /**
     * Builds the complete list of menu foods by merging database records,
     * applying overrides from data/menu-overrides.json, and appending custom foods.
     */
    public List<Food> getAllMergedFoods() {
        MenuOverrideData overrides = menuFileService.loadOverrides();
        List<Food> result = new ArrayList<>();

        // Add database foods with overrides applied
        for (Food dbFood : foodRepository.findAll()) {
            Food food = copyFood(dbFood);
            applyOverrides(food, overrides);
            result.add(food);
        }

        // Add custom foods from file with overrides applied
        for (Food customFood : overrides.getCustomItems()) {
            Food food = copyFood(customFood);
            applyOverrides(food, overrides);
            result.add(food);
        }

        return result;
    }

    private void applyOverrides(Food food) {
        MenuOverrideData overrides = menuFileService.loadOverrides();
        applyOverrides(food, overrides);
    }

    private void applyOverrides(Food food, MenuOverrideData overrides) {
        if (food == null || food.getId() == null || overrides == null) return;

        if (overrides.getPriceOverrides().containsKey(food.getId())) {
            food.setPrice(overrides.getPriceOverrides().get(food.getId()));
        }

        if (overrides.getAvailabilityOverrides().containsKey(food.getId())) {
            food.setAvailable(overrides.getAvailabilityOverrides().get(food.getId()));
        }
    }

    private Food copyFood(Food source) {
        return new Food(
                source.getId(),
                source.getName(),
                source.getCategory(),
                source.getDescription(),
                source.getPrice(),
                source.getRating(),
                source.getBadge(),
                source.getImage(),
                source.isAvailable()
        );
    }
}

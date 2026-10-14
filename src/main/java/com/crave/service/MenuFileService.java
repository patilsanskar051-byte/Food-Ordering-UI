package com.crave.service;

import com.crave.dto.MenuOverrideData;
import com.crave.entity.Food;
import com.crave.exception.FileStorageException;
import com.crave.exception.MenuValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing file-based menu overrides and custom food additions in data/menu-overrides.json.
 * Demonstrates Java File Handling (Files, Paths, Buffers), JSON persistence,
 * encapsulation, and business validation.
 */
@Service
public class MenuFileService {

    private final Path menuFilePath;
    private final ObjectMapper objectMapper;

    public MenuFileService(
            @Value("${crave.menu-overrides.file:./data/menu-overrides.json}") String filePath,
            ObjectMapper objectMapper) {
        this.menuFilePath = Paths.get(filePath).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        initializeFileStorage();
    }

    /**
     * Initializes directory and default file if they do not yet exist.
     */
    private void initializeFileStorage() {
        try {
            Path parentDir = menuFilePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            if (!Files.exists(menuFilePath)) {
                MenuOverrideData initialData = new MenuOverrideData();
                saveOverrides(initialData);
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to initialize menu overrides storage", menuFilePath.toString(), e);
        }
    }

    /**
     * Loads the menu override configuration from the JSON file.
     * Safely recovers if the file is missing or corrupted.
     */
    public synchronized MenuOverrideData loadOverrides() {
        if (!Files.exists(menuFilePath)) {
            return new MenuOverrideData();
        }

        try {
            String json = Files.readString(menuFilePath, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) {
                return new MenuOverrideData();
            }
            return objectMapper.readValue(json, MenuOverrideData.class);
        } catch (Exception e) {
            System.err.println("Warning: Corrupted menu-overrides.json detected. Re-initializing empty overrides: " + e.getMessage());
            MenuOverrideData fallback = new MenuOverrideData();
            saveOverrides(fallback);
            return fallback;
        }
    }

    /**
     * Writes the current menu override configuration to the JSON file using an atomic
     * write pattern (writing to a temp file and moving it into place) to prevent file corruption.
     */
    public synchronized void saveOverrides(MenuOverrideData data) {
        data.setLastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        Path tempFilePath = menuFilePath.resolveSibling(menuFilePath.getFileName().toString() + ".tmp");
        try {
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.writeString(
                    tempFilePath,
                    prettyJson,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            try {
                Files.move(tempFilePath, menuFilePath, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tempFilePath, menuFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to write menu overrides to file", menuFilePath.toString(), e);
        } finally {
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Updates the price for a specific food item and saves to file.
     * Enforces positive price validation.
     */
    public synchronized void updatePrice(Long foodId, int newPrice) {
        if (foodId == null || foodId <= 0) {
            throw new MenuValidationException("id", "Invalid food item ID.");
        }
        if (newPrice <= 0) {
            throw new MenuValidationException("price", "Price must be a positive integer greater than 0.");
        }

        MenuOverrideData data = loadOverrides();
        data.getPriceOverrides().put(foodId, newPrice);

        // If this ID corresponds to a custom food item, also update that custom item
        for (Food custom : data.getCustomItems()) {
            if (custom.getId().equals(foodId)) {
                custom.setPrice(newPrice);
                break;
            }
        }

        saveOverrides(data);
    }

    /**
     * Updates the availability status (in stock / out of stock) for a food item and saves to file.
     */
    public synchronized void updateAvailability(Long foodId, boolean available) {
        if (foodId == null || foodId <= 0) {
            throw new MenuValidationException("id", "Invalid food item ID.");
        }

        MenuOverrideData data = loadOverrides();
        data.getAvailabilityOverrides().put(foodId, available);

        // If this ID corresponds to a custom food item, also update that custom item
        for (Food custom : data.getCustomItems()) {
            if (custom.getId().equals(foodId)) {
                custom.setAvailable(available);
                break;
            }
        }

        saveOverrides(data);
    }

    /**
     * Adds a new custom food item to the menu and persists it to the overrides file.
     * Validates name, category, and price.
     */
    public synchronized Food addCustomFood(Food foodInput, long maxExistingId) {
        if (foodInput == null) {
            throw new MenuValidationException("item", "Food item payload cannot be null.");
        }

        // Validation
        if (foodInput.getName() == null || foodInput.getName().trim().isEmpty()) {
            throw new MenuValidationException("name", "Food name is required and cannot be empty.");
        }
        if (foodInput.getCategory() == null || foodInput.getCategory().trim().isEmpty()) {
            throw new MenuValidationException("category", "Category is required and cannot be empty.");
        }
        if (foodInput.getPrice() <= 0) {
            throw new MenuValidationException("price", "Price must be greater than ₹0.");
        }

        MenuOverrideData data = loadOverrides();

        // Calculate a safe unique ID
        long nextId = Math.max(maxExistingId, 9L) + 1L;
        for (Food item : data.getCustomItems()) {
            if (item.getId() != null && item.getId() >= nextId) {
                nextId = item.getId() + 1L;
            }
        }

        Food newFood = new Food();
        newFood.setId(nextId);
        newFood.setName(foodInput.getName().trim());
        newFood.setCategory(foodInput.getCategory().trim());
        newFood.setDescription(
                foodInput.getDescription() != null && !foodInput.getDescription().trim().isEmpty()
                        ? foodInput.getDescription().trim()
                        : "Freshly prepared house specialty."
        );
        newFood.setPrice(foodInput.getPrice());
        newFood.setRating(
                foodInput.getRating() != null && !foodInput.getRating().trim().isEmpty()
                        ? foodInput.getRating().trim()
                        : "4.9"
        );
        newFood.setBadge(
                foodInput.getBadge() != null && !foodInput.getBadge().trim().isEmpty()
                        ? foodInput.getBadge().trim()
                        : "NEW"
        );
        newFood.setImage(
                foodInput.getImage() != null && !foodInput.getImage().trim().isEmpty()
                        ? foodInput.getImage().trim()
                        : "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=600&q=75"
        );
        newFood.setAvailable(foodInput.isAvailable());

        data.getCustomItems().add(newFood);
        saveOverrides(data);

        return newFood;
    }

    /**
     * Retrieves custom foods from file.
     */
    public synchronized List<Food> getCustomFoods() {
        return new ArrayList<>(loadOverrides().getCustomItems());
    }

    /**
     * Finds a custom food by ID from file.
     */
    public synchronized Optional<Food> findCustomFoodById(Long id) {
        if (id == null) return Optional.empty();
        MenuOverrideData data = loadOverrides();
        return data.getCustomItems().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst();
    }

    /**
     * Checks if a food item is a file-backed custom food item.
     */
    public synchronized boolean isCustomFood(Long id) {
        if (id == null) return false;
        return findCustomFoodById(id).isPresent();
    }

    /**
     * Deletes a custom food item or removes overrides for a standard item.
     * Persists the resulting state cleanly to data/menu-overrides.json.
     *
     * @param foodId the food ID to delete or reset
     * @return true if an item was removed or an override was cleared
     */
    public synchronized boolean deleteFoodItemOrOverride(Long foodId) {
        if (foodId == null || foodId <= 0) {
            throw new MenuValidationException("id", "Invalid food item ID for deletion.");
        }

        MenuOverrideData data = loadOverrides();
        boolean removed = false;

        // If it's a custom food item, remove it from customItems list
        boolean removedFromCustom = data.getCustomItems().removeIf(item -> foodId.equals(item.getId()));
        if (removedFromCustom) {
            removed = true;
        }

        // Also clean up any price or availability overrides for this ID
        if (data.getPriceOverrides().remove(foodId) != null) {
            removed = true;
        }
        if (data.getAvailabilityOverrides().remove(foodId) != null) {
            removed = true;
        }

        if (removed) {
            saveOverrides(data);
        }

        return removed;
    }

    /**
     * Path to the file for diagnostics or logging.
     */
    public Path getMenuFilePath() {
        return menuFilePath;
    }
}

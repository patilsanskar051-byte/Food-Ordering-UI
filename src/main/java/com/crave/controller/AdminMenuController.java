package com.crave.controller;

import com.crave.dto.MenuOverrideData;
import com.crave.entity.Food;
import com.crave.entity.Order;
import com.crave.exception.MenuValidationException;
import com.crave.repository.OrderRepository;
import com.crave.service.FoodService;
import com.crave.service.MenuFileService;
import com.crave.service.OrderHistoryFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for Admin Menu Management (Feature 3).
 *
 * Protection Strategy:
 * Every administrative operation requires an 'X-Admin-Token' HTTP header matching the configured
 * secret (crave.admin.secret).
 * Ordinary customer requests without or with an invalid token receive HTTP 403 Forbidden.
 *
 * Security Limitation Note:
 * This uses a shared secret header suitable for course demonstrations and single-tenant internal tools.
 * It lacks multi-user credentials, salted password hashing, RBAC roles, and token expiration.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMenuController {

    private final MenuFileService menuFileService;
    private final FoodService foodService;
    private final OrderRepository orderRepository;
    private final OrderHistoryFileService orderHistoryFileService;
    private final String configuredAdminSecret;

    @Autowired
    public AdminMenuController(
            MenuFileService menuFileService,
            FoodService foodService,
            OrderRepository orderRepository,
            OrderHistoryFileService orderHistoryFileService,
            @Value("${crave.admin.secret:crave-admin-secret-2026}") String adminSecret) {
        this.menuFileService = menuFileService;
        this.foodService = foodService;
        this.orderRepository = orderRepository;
        this.orderHistoryFileService = orderHistoryFileService;
        this.configuredAdminSecret = adminSecret;
    }

    /**
     * Overloaded constructor for tests or lightweight initialization.
     */
    public AdminMenuController(
            MenuFileService menuFileService,
            FoodService foodService,
            String adminSecret) {
        this(menuFileService, foodService, null, null, adminSecret);
    }

    /**
     * Validates the provided admin token header.
     * Throws SecurityException if token is missing or incorrect.
     */
    private void authenticateAdmin(String token) {
        if (token == null || token.trim().isEmpty() || !configuredAdminSecret.equals(token.trim())) {
            throw new SecurityException("Unauthorized access: A valid 'X-Admin-Token' header is required.");
        }
    }

    /**
     * POST /api/admin/verify
     * Verifies whether an admin token is valid without making changes.
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyAdmin(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authenticateAdmin(token);
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "message", "Admin session verified successfully."
        ));
    }

    /**
     * GET /api/admin/menu/overrides
     * Returns current raw overrides data stored in data/menu-overrides.json.
     */
    @GetMapping("/menu/overrides")
    public ResponseEntity<MenuOverrideData> getMenuOverrides(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authenticateAdmin(token);
        return ResponseEntity.ok(menuFileService.loadOverrides());
    }

    /**
     * PUT /api/admin/menu/items/{id}/price
     * Updates the price of an existing item and writes to data/menu-overrides.json.
     */
    @PutMapping("/menu/items/{id}/price")
    public ResponseEntity<Map<String, Object>> updatePrice(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authenticateAdmin(token);

        Object priceObj = payload.get("price");
        if (priceObj == null) {
            throw new MenuValidationException("price", "Price is required in request payload.");
        }

        int newPrice;
        try {
            newPrice = Integer.parseInt(priceObj.toString());
        } catch (NumberFormatException e) {
            throw new MenuValidationException("price", "Price must be a valid integer.");
        }

        menuFileService.updatePrice(id, newPrice);
        Food updatedFood = foodService.getFoodById(id).orElse(null);

        return ResponseEntity.ok(Map.of(
                "message", "Price updated successfully to ₹" + newPrice,
                "food", updatedFood != null ? updatedFood : Map.of("id", id, "price", newPrice)
        ));
    }

    /**
     * PUT /api/admin/menu/items/{id}/availability
     * Toggles/updates the stock availability of an item and writes to data/menu-overrides.json.
     */
    @PutMapping("/menu/items/{id}/availability")
    public ResponseEntity<Map<String, Object>> updateAvailability(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authenticateAdmin(token);

        Object availObj = payload.get("available");
        if (availObj == null) {
            throw new MenuValidationException("available", "Availability status is required in request payload.");
        }

        boolean available = Boolean.parseBoolean(availObj.toString());
        menuFileService.updateAvailability(id, available);
        Food updatedFood = foodService.getFoodById(id).orElse(null);

        return ResponseEntity.ok(Map.of(
                "message", "Availability updated successfully to " + (available ? "IN STOCK" : "OUT OF STOCK"),
                "food", updatedFood != null ? updatedFood : Map.of("id", id, "available", available)
        ));
    }

    /**
     * POST /api/admin/menu/items
     * Adds a brand-new food item to the menu and persists it to data/menu-overrides.json.
     */
    @PostMapping("/menu/items")
    public ResponseEntity<Map<String, Object>> addFoodItem(
            @RequestBody Food foodInput,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authenticateAdmin(token);

        long maxId = foodService.getMaxFoodId();
        Food addedFood = menuFileService.addCustomFood(foodInput, maxId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "New food item added successfully to menu.",
                "food", addedFood
        ));
    }

    /**
     * GET /api/admin/stats
     * Computes real-time dashboard analytics from actual persisted orders and menu data.
     * Never fabricates fake statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAdminStats(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authenticateAdmin(token);

        long totalOrders = 0;
        long totalRevenue = 0;

        if (orderRepository != null) {
            List<Order> dbOrders = orderRepository.findAll();
            totalOrders = dbOrders.size();
            for (Order o : dbOrders) {
                totalRevenue += o.getTotalAmount();
            }
        } else if (orderHistoryFileService != null) {
            var fileOrders = orderHistoryFileService.readOrderHistory();
            totalOrders = fileOrders.size();
            for (var o : fileOrders) {
                totalRevenue += o.getTotalAmount();
            }
        }

        List<Food> allFoods = foodService.getAllMergedFoods();
        long totalMenuItems = allFoods.size();
        long outOfStockCount = allFoods.stream().filter(f -> !f.isAvailable()).count();
        long customItemsCount = menuFileService.getCustomFoods().size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders", totalOrders);
        stats.put("totalRevenue", totalRevenue);
        stats.put("totalMenuItems", totalMenuItems);
        stats.put("outOfStockCount", outOfStockCount);
        stats.put("inStockCount", totalMenuItems - outOfStockCount);
        stats.put("customItemsCount", customItemsCount);

        return ResponseEntity.ok(stats);
    }

    /**
     * DELETE /api/admin/menu/items/{id}
     * Deletes a custom food item or clears overrides for a seeded item.
     * Persists change to data/menu-overrides.json.
     */
    @DeleteMapping("/menu/items/{id}")
    public ResponseEntity<Map<String, Object>> deleteFoodItem(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authenticateAdmin(token);

        boolean isCustom = menuFileService.isCustomFood(id);
        boolean deleted = menuFileService.deleteFoodItemOrOverride(id);

        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Item Not Found",
                    "message", "No custom food item or active override found for ID: " + id,
                    "status", HttpStatus.NOT_FOUND.value()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", isCustom ? "Custom food item removed successfully." : "Menu overrides cleared for standard item.",
                "id", id,
                "wasCustom", isCustom
        ));
    }
}

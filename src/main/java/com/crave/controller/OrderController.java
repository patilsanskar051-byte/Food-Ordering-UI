package com.crave.controller;

import com.crave.dto.OrderHistoryRecord;
import com.crave.dto.OrderRequest;
import com.crave.entity.Order;
import com.crave.service.AuthService;
import com.crave.service.OrderHistoryFileService;
import com.crave.service.OrderService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for order endpoints.
 * Provides both H2 database-backed order access and persistent file-based order history.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderHistoryFileService orderHistoryFileService;
    private AuthService authService;

    public OrderController(OrderService orderService, OrderHistoryFileService orderHistoryFileService) {
        this(orderService, orderHistoryFileService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OrderController(OrderService orderService, OrderHistoryFileService orderHistoryFileService, AuthService authService) {
        this.orderService = orderService;
        this.orderHistoryFileService = orderHistoryFileService;
        this.authService = authService;
    }

    /**
     * POST /api/orders
     *
     * Creates a new order. Expects JSON body with items array.
     * Returns the created order with 201 status.
     * Automatically persists to H2, generates plain-text receipt, and records to history file.
     */
    @PostMapping
    public ResponseEntity<?> createOrder(@Valid @RequestBody OrderRequest request, HttpSession session) {
        try {
            if (authService != null && session != null) {
                authService.getCurrentUser(session).ifPresent(u -> {
                    if (request.getCustomerName() == null || request.getCustomerName().trim().isEmpty() || "Guest User".equalsIgnoreCase(request.getCustomerName().trim())) {
                        request.setCustomerName(u.getFullName());
                    }
                });
            }
            Order order = orderService.createOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", e.getMessage())
            );
        }
    }

    /**
     * GET /api/orders
     *
     * Returns all orders from the H2 database, newest first.
     */
    @GetMapping
    public List<Order> getAllOrders() {
        return orderService.getAllOrders();
    }

    /**
     * GET /api/orders/history/file
     *
     * Feature 2: Retrieves orders persisted in the file data/order-history.jsonl.
     */
    @GetMapping("/history/file")
    public List<OrderHistoryRecord> getFileOrderHistory() {
        return orderHistoryFileService.readOrderHistory();
    }

    /**
     * GET /api/orders/history/file/stats
     *
     * Diagnostics endpoint returning file storage metadata.
     */
    @GetMapping("/history/file/stats")
    public ResponseEntity<Map<String, Object>> getFileHistoryStats() {
        Map<String, Object> stats = new HashMap<>();
        List<OrderHistoryRecord> records = orderHistoryFileService.readOrderHistory();
        stats.put("totalRecords", records.size());
        stats.put("filePath", orderHistoryFileService.getHistoryFilePath().toString());
        stats.put("fileExists", Files.exists(orderHistoryFileService.getHistoryFilePath()));
        try {
            if (Files.exists(orderHistoryFileService.getHistoryFilePath())) {
                stats.put("fileSizeBytes", Files.size(orderHistoryFileService.getHistoryFilePath()));
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/orders/{id}
     *
     * Returns a single order from database, or 404 if not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

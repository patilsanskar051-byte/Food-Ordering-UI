package com.crave.service;

import com.crave.dto.OrderRequest;
import com.crave.entity.Food;
import com.crave.entity.Order;
import com.crave.entity.OrderItem;
import com.crave.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for order-related business logic.
 * Validates food IDs, stock availability, and quantities.
 * Calculates totals server-side using current verified prices.
 * Automatically generates plain-text receipts and appends to persistent order history file.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final FoodService foodService;
    private final ReceiptService receiptService;
    private final OrderHistoryFileService orderHistoryFileService;

    public OrderService(
            OrderRepository orderRepository,
            FoodService foodService,
            ReceiptService receiptService,
            OrderHistoryFileService orderHistoryFileService) {
        this.orderRepository = orderRepository;
        this.foodService = foodService;
        this.receiptService = receiptService;
        this.orderHistoryFileService = orderHistoryFileService;
    }

    /**
     * Create a new order from the request.
     * - Validates that items list is not empty.
     * - Validates each food exists (in DB or custom items) and is IN STOCK.
     * - Validates each quantity is at least 1.
     * - Calculates totals using server-side prices (never trusts frontend values).
     * - Saves to H2 database.
     * - Generates plain-text receipt on disk via ReceiptService.
     * - Records order snapshot into persistent JSONL file via OrderHistoryFileService.
     */
    public Order createOrder(OrderRequest request) {
        List<OrderRequest.OrderItemRequest> requestItems = request.getItems();

        if (requestItems == null || requestItems.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }

        Order order = new Order();
        int totalAmount = 0;

        for (OrderRequest.OrderItemRequest item : requestItems) {
            // Validate quantity
            if (item.getQuantity() < 1) {
                throw new IllegalArgumentException(
                    "Quantity must be at least 1 for food ID " + item.getFoodId() + "."
                );
            }

            // Validate food exists and is in stock
            Food food = foodService.getFoodById(item.getFoodId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Food item with ID " + item.getFoodId() + " not found."
                ));

            if (!food.isAvailable()) {
                throw new IllegalArgumentException(
                    "Food item '" + food.getName() + "' is currently out of stock."
                );
            }

            // Create order item with verified server-side price (matches menu overrides)
            OrderItem orderItem = new OrderItem(
                food.getId(),
                food.getName(),
                food.getPrice(),
                item.getQuantity()
            );

            order.addItem(orderItem);
            totalAmount += orderItem.getSubtotal();
        }

        order.setTotalAmount(totalAmount);

        // Cleanly sanitize customer name and delivery address with meaningful defaults
        String customerName = (request.getCustomerName() != null && !request.getCustomerName().trim().isEmpty())
                ? request.getCustomerName().trim()
                : "Guest User";
        order.setCustomerName(customerName);

        String deliveryAddress = (request.getDeliveryAddress() != null && !request.getDeliveryAddress().trim().isEmpty())
                ? request.getDeliveryAddress().trim()
                : "Campus Food Court";
        order.setDeliveryAddress(deliveryAddress);

        // 1. Persist in H2 Database (Primary source of truth)
        Order savedOrder = orderRepository.save(order);

        // Dual-Write / Non-XA Architectural Consistency Note:
        // Relational database transactions and local file storage are not coordinated by a 2PC/XA transaction.
        // Therefore, we treat the database as the authoritative source of truth. File operations (receipt generation
        // and JSONL history appending) execute in isolated try-catch blocks to ensure that transient filesystem
        // I/O failures do not discard or fail the customer's persisted order.

        // 2. Feature 1: Automatic Order Receipt Generator
        try {
            receiptService.generateReceipt(savedOrder);
        } catch (Exception e) {
            System.err.println("Warning: Failed to generate receipt on disk for order #" + savedOrder.getId() + ": " + e.getMessage());
        }

        // 3. Feature 2: Persistent File-Based Order History
        try {
            orderHistoryFileService.recordOrder(savedOrder);
        } catch (Exception e) {
            System.err.println("Warning: Failed to record order #" + savedOrder.getId() + " in history file: " + e.getMessage());
        }

        return savedOrder;
    }

    /**
     * Get all orders from the H2 database, sorted by most recent first.
     */
    public List<Order> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        // Sort by createdAt descending (newest first)
        orders.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        return orders;
    }

    /**
     * Get a single order by ID from the H2 database.
     */
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
}

package com.crave.dto;

import com.crave.entity.Order;
import com.crave.entity.OrderItem;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Data model representing a file-persisted order record in JSON Lines format.
 * Encapsulates order snapshot data stored persistently on disk.
 */
public class OrderHistoryRecord {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private Long orderId;
    private String customerName;
    private String deliveryAddress;
    private String createdAt;
    private int totalAmount;
    private String status;
    private List<ItemRecord> items = new ArrayList<>();
    private String recordedAt;

    public OrderHistoryRecord() {
    }

    /**
     * Constructs a history record directly from an existing JPA Order entity.
     */
    public static OrderHistoryRecord fromOrder(Order order) {
        OrderHistoryRecord record = new OrderHistoryRecord();
        record.setOrderId(order.getId());
        record.setCustomerName(order.getCustomerName());
        record.setDeliveryAddress(order.getDeliveryAddress());
        record.setTotalAmount(order.getTotalAmount());
        record.setStatus(order.getStatus());
        record.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().format(ISO_FORMATTER) : LocalDateTime.now().format(ISO_FORMATTER));
        record.setRecordedAt(LocalDateTime.now().format(ISO_FORMATTER));

        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                record.getItems().add(new ItemRecord(
                        item.getFoodId(),
                        item.getFoodName(),
                        item.getPricePerItem(),
                        item.getQuantity(),
                        item.getSubtotal()
                ));
            }
        }
        return record;
    }

    // --- Getters & Setters ---

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(int totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<ItemRecord> getItems() {
        return items;
    }

    public void setItems(List<ItemRecord> items) {
        this.items = items;
    }

    public String getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(String recordedAt) {
        this.recordedAt = recordedAt;
    }

    /**
     * Inner item model for line-item order details.
     */
    public static class ItemRecord {
        private Long foodId;
        private String foodName;
        private int pricePerItem;
        private int quantity;
        private int subtotal;

        public ItemRecord() {
        }

        public ItemRecord(Long foodId, String foodName, int pricePerItem, int quantity, int subtotal) {
            this.foodId = foodId;
            this.foodName = foodName;
            this.pricePerItem = pricePerItem;
            this.quantity = quantity;
            this.subtotal = subtotal;
        }

        public Long getFoodId() {
            return foodId;
        }

        public void setFoodId(Long foodId) {
            this.foodId = foodId;
        }

        public String getFoodName() {
            return foodName;
        }

        public void setFoodName(String foodName) {
            this.foodName = foodName;
        }

        public int getPricePerItem() {
            return pricePerItem;
        }

        public void setPricePerItem(int pricePerItem) {
            this.pricePerItem = pricePerItem;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public int getSubtotal() {
            return subtotal;
        }

        public void setSubtotal(int subtotal) {
            this.subtotal = subtotal;
        }
    }
}

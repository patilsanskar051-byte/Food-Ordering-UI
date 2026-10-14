package com.crave.service;

import com.crave.entity.Order;
import com.crave.entity.OrderItem;
import com.crave.exception.FileStorageException;
import com.crave.exception.ReceiptNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;

/**
 * Service responsible for generating, managing, and reading plain-text order receipts.
 * Demonstrates Java File Handling (NIO Path, Files, BufferedWriter), encapsulation,
 * path traversal defense, and exception handling.
 */
@Service
public class ReceiptService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    private final Path receiptsDirectory;

    public ReceiptService(@Value("${crave.receipts.dir:./data/receipts}") String receiptsDir) {
        this.receiptsDirectory = Paths.get(receiptsDir).toAbsolutePath().normalize();
        initializeDirectory();
    }

    /**
     * Ensures the receipt storage directory exists upon service initialization.
     */
    private void initializeDirectory() {
        try {
            if (!Files.exists(receiptsDirectory)) {
                Files.createDirectories(receiptsDirectory);
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to initialize receipt storage directory", receiptsDirectory.toString(), e);
        }
    }

    /**
     * Generates a plain-text receipt for the given saved Order.
     * Calculated entirely from verified server-side Order entity data.
     * Avoids duplication if an identical receipt already exists.
     *
     * @param order the persisted Order entity
     * @return Path to the generated receipt file
     */
    public synchronized Path generateReceipt(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Cannot generate receipt for a null order or an order without an ID.");
        }

        Path receiptPath = resolveSafeReceiptPath(order.getId());

        // Avoid unnecessary duplicate write if receipt already exists
        if (Files.exists(receiptPath)) {
            return receiptPath;
        }

        String receiptText = buildReceiptText(order);

        try (BufferedWriter writer = Files.newBufferedWriter(
                receiptPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(receiptText);
            writer.flush();
            return receiptPath;
        } catch (IOException e) {
            throw new FileStorageException("Failed to write receipt for order #" + order.getId(), receiptPath.toString(), e);
        }
    }

    /**
     * Reads the text content of a receipt for a given order ID.
     */
    public String getReceiptContent(Long orderId) {
        Path receiptPath = resolveSafeReceiptPath(orderId);
        if (!Files.exists(receiptPath)) {
            throw new ReceiptNotFoundException(orderId);
        }

        try {
            return Files.readString(receiptPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FileStorageException("Failed to read receipt file for order #" + orderId, receiptPath.toString(), e);
        }
    }

    /**
     * Resolves the Path of an existing receipt file for download.
     */
    public Path getReceiptFilePath(Long orderId) {
        Path receiptPath = resolveSafeReceiptPath(orderId);
        if (!Files.exists(receiptPath)) {
            throw new ReceiptNotFoundException(orderId);
        }
        return receiptPath;
    }

    /**
     * Checks if a receipt file currently exists on disk.
     */
    public boolean receiptExists(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return false;
        }
        Path receiptPath = resolveSafeReceiptPath(orderId);
        return Files.exists(receiptPath);
    }

    /**
     * Resolves a sanitized, canonical receipt file path and prevents directory traversal attacks.
     */
    private Path resolveSafeReceiptPath(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Invalid order ID: " + orderId);
        }
        String fileName = "receipt-order-" + orderId + ".txt";
        Path resolvedPath = receiptsDirectory.resolve(fileName).normalize();

        // Path Traversal Security Check
        if (!resolvedPath.startsWith(receiptsDirectory)) {
            throw new SecurityException("Path traversal attempt detected with order ID: " + orderId);
        }

        return resolvedPath;
    }

    /**
     * Constructs a professional, neatly aligned plain-text receipt.
     */
    private String buildReceiptText(Order order) {
        StringBuilder sb = new StringBuilder();
        String dateStr = order.getCreatedAt() != null
                ? order.getCreatedAt().format(DATE_FORMATTER)
                : "N/A";

        sb.append("================================================================\n");
        sb.append("                      CRAVE FOOD ORDERING                      \n");
        sb.append("                    OFFICIAL ORDER RECEIPT                     \n");
        sb.append("================================================================\n");
        sb.append(String.format("Receipt No       : REC-%06d\n", order.getId()));
        sb.append(String.format("Order ID         : #%d\n", order.getId()));
        sb.append(String.format("Order Date & Time: %s\n", dateStr));
        sb.append(String.format("Order Status     : %s\n", order.getStatus()));
        sb.append("----------------------------------------------------------------\n");
        sb.append("CUSTOMER DETAILS:\n");
        sb.append(String.format("Customer Name    : %s\n", order.getCustomerName()));
        sb.append(String.format("Delivery Address : %s\n", order.getDeliveryAddress()));
        sb.append("----------------------------------------------------------------\n");
        sb.append(String.format("%-32s %5s   %8s   %9s\n", "Item", "Qty", "Price", "Subtotal"));
        sb.append("----------------------------------------------------------------\n");

        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                String foodName = item.getFoodName();
                if (foodName.length() > 30) {
                    foodName = foodName.substring(0, 27) + "...";
                }
                sb.append(String.format("%-32s %5d   ₹%7d   ₹%8d\n",
                        foodName,
                        item.getQuantity(),
                        item.getPricePerItem(),
                        item.getSubtotal()));
            }
        }

        sb.append("----------------------------------------------------------------\n");
        sb.append(String.format("%-48s ₹%8d\n", "SUBTOTAL:", order.getTotalAmount()));
        sb.append(String.format("%-48s %9s\n", "DELIVERY CHARGE:", "FREE"));
        sb.append("================================================================\n");
        sb.append(String.format("%-48s ₹%8d\n", "FINAL TOTAL (PAID):", order.getTotalAmount()));
        sb.append("================================================================\n");
        sb.append("               Thank you for ordering with Crave!               \n");
        sb.append("                Fresh flavours, happy hearts.                   \n");
        sb.append("           For support: support@crave.demo | +91 98765 43210    \n");
        sb.append("================================================================\n");

        return sb.toString();
    }
}

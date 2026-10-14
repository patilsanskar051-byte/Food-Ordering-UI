package com.crave.service;

import com.crave.dto.OrderHistoryRecord;
import com.crave.entity.Order;
import com.crave.exception.FileStorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for managing persistent file-based order history using JSON Lines format.
 * Demonstrates Java File Handling (Streams/Readers/Writers), Collections,
 * idempotency, and resilient parsing of malformed records.
 */
@Service
public class OrderHistoryFileService {

    private final Path historyFilePath;
    private final ObjectMapper objectMapper;

    public OrderHistoryFileService(
            @Value("${crave.order-history.file:./data/order-history.jsonl}") String filePath,
            ObjectMapper objectMapper) {
        this.historyFilePath = Paths.get(filePath).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        initializeFileStorage();
    }

    /**
     * Initializes the storage folder and file if necessary.
     */
    private void initializeFileStorage() {
        try {
            Path parentDir = historyFilePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to initialize order history storage directory", historyFilePath.toString(), e);
        }
    }

    /**
     * Appends a newly placed order to the persistent JSONL history file.
     * Guaranteed to be idempotent: skips writing if the order ID is already present.
     *
     * @param order the persisted Order entity
     */
    public synchronized void recordOrder(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Cannot record a null order or an order without an ID.");
        }

        // Avoid duplicate history records if order was already written
        if (isOrderRecorded(order.getId())) {
            System.out.println("Order #" + order.getId() + " is already present in order-history.jsonl. Skipping duplicate write.");
            return;
        }

        OrderHistoryRecord record = OrderHistoryRecord.fromOrder(order);

        try {
            String jsonLine = objectMapper.writeValueAsString(record);
            try (BufferedWriter writer = Files.newBufferedWriter(
                    historyFilePath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {
                writer.write(jsonLine);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to append order #" + order.getId() + " to history file", historyFilePath.toString(), e);
        }
    }

    /**
     * Reads all recorded orders from the JSONL history file.
     * Robust against missing files, blank lines, and malformed individual records.
     *
     * @return List of OrderHistoryRecords sorted newest first
     */
    public synchronized List<OrderHistoryRecord> readOrderHistory() {
        if (!Files.exists(historyFilePath)) {
            return Collections.emptyList();
        }

        List<OrderHistoryRecord> records = new ArrayList<>();
        int lineNumber = 0;

        try (BufferedReader reader = Files.newBufferedReader(historyFilePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    OrderHistoryRecord record = objectMapper.readValue(trimmed, OrderHistoryRecord.class);
                    records.add(record);
                } catch (Exception parseEx) {
                    // Safe handling of malformed records: log warning and continue without breaking entire history
                    System.err.println("Warning: Skipping malformed record on line " + lineNumber + " in " + historyFilePath + ": " + parseEx.getMessage());
                }
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to read order history file", historyFilePath.toString(), e);
        }

        // Sort descending by order ID (newest first)
        records.sort((a, b) -> {
            if (a.getOrderId() == null || b.getOrderId() == null) return 0;
            return b.getOrderId().compareTo(a.getOrderId());
        });

        return records;
    }

    /**
     * Checks if a given order ID is already recorded in the history file.
     */
    public synchronized boolean isOrderRecorded(Long orderId) {
        if (!Files.exists(historyFilePath) || orderId == null) {
            return false;
        }

        Set<Long> existingIds = getRecordedOrderIds();
        return existingIds.contains(orderId);
    }

    /**
     * Retrieves the set of all order IDs currently recorded in the file.
     */
    public synchronized Set<Long> getRecordedOrderIds() {
        if (!Files.exists(historyFilePath)) {
            return Collections.emptySet();
        }

        Set<Long> idSet = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(historyFilePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    OrderHistoryRecord record = objectMapper.readValue(trimmed, OrderHistoryRecord.class);
                    if (record.getOrderId() != null) {
                        idSet.add(record.getOrderId());
                    }
                } catch (Exception ignored) {
                    // Ignore malformed lines during ID scan
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Error scanning order IDs from history file: " + e.getMessage());
        }
        return idSet;
    }

    /**
     * Returns the absolute path of the history file.
     */
    public Path getHistoryFilePath() {
        return historyFilePath;
    }
}

package com.crave.service;

import com.crave.dto.OrderHistoryRecord;
import com.crave.entity.Order;
import com.crave.entity.OrderItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderHistoryFileServiceTest {

    @TempDir
    Path tempDir;

    private OrderHistoryFileService historyService;
    private Path historyFile;

    @BeforeEach
    void setUp() {
        historyFile = tempDir.resolve("test-order-history.jsonl");
        historyService = new OrderHistoryFileService(historyFile.toString(), new ObjectMapper());
    }

    @Test
    void testRecordAndReadOrderHistory() {
        Order order1 = new Order();
        order1.setId(101L);
        order1.setCustomerName("Rohan");
        order1.setDeliveryAddress("Block C");
        order1.setCreatedAt(LocalDateTime.now());
        order1.setTotalAmount(199);
        order1.addItem(new OrderItem(2L, "Margherita Pizza", 199, 1));

        Order order2 = new Order();
        order2.setId(102L);
        order2.setCustomerName("Priya");
        order2.setDeliveryAddress("Block D");
        order2.setCreatedAt(LocalDateTime.now().plusMinutes(5));
        order2.setTotalAmount(99);
        order2.addItem(new OrderItem(6L, "Iced Caramel Coffee", 99, 1));

        historyService.recordOrder(order1);
        historyService.recordOrder(order2);

        List<OrderHistoryRecord> records = historyService.readOrderHistory();
        assertEquals(2, records.size());
        // Newest first
        assertEquals(102L, records.get(0).getOrderId());
        assertEquals("Priya", records.get(0).getCustomerName());
        assertEquals(101L, records.get(1).getOrderId());
        assertEquals("Rohan", records.get(1).getCustomerName());
    }

    @Test
    void testPreventDuplicateRecords() {
        Order order = new Order();
        order.setId(201L);
        order.setCustomerName("Same Customer");
        order.setTotalAmount(149);
        order.addItem(new OrderItem(1L, "Classic Smash Burger", 149, 1));

        historyService.recordOrder(order);
        historyService.recordOrder(order); // duplicate call

        List<OrderHistoryRecord> records = historyService.readOrderHistory();
        assertEquals(1, records.size());
        assertEquals(201L, records.get(0).getOrderId());
    }

    @Test
    void testResilientParsingWithMalformedLines() throws IOException {
        // Pre-populate with a valid record, a malformed line, and another valid record
        String line1 = "{\"orderId\":301,\"customerName\":\"User 1\",\"totalAmount\":100,\"items\":[]}\n";
        String corruptedLine = "THIS_IS_CORRUPTED_JSON_DATA_NOT_VALID_JSON\n";
        String line2 = "{\"orderId\":302,\"customerName\":\"User 2\",\"totalAmount\":200,\"items\":[]}\n";

        Files.writeString(historyFile, line1 + corruptedLine + line2, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        List<OrderHistoryRecord> records = historyService.readOrderHistory();
        // Malformed line should be safely skipped; both valid records parsed
        assertEquals(2, records.size());
        assertTrue(records.stream().anyMatch(r -> r.getOrderId() == 301L));
        assertTrue(records.stream().anyMatch(r -> r.getOrderId() == 302L));
    }

    @Test
    void testMissingFileReturnsEmptyList() {
        Path nonExistent = tempDir.resolve("missing.jsonl");
        OrderHistoryFileService missingService = new OrderHistoryFileService(nonExistent.toString(), new ObjectMapper());
        List<OrderHistoryRecord> records = missingService.readOrderHistory();
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }
}

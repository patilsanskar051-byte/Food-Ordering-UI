package com.crave.service;

import com.crave.entity.Order;
import com.crave.entity.OrderItem;
import com.crave.exception.ReceiptNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptServiceTest {

    @TempDir
    Path tempDir;

    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptService(tempDir.toString());
    }

    @Test
    void testGenerateAndReadReceipt() throws IOException {
        Order order = new Order();
        order.setId(42L);
        order.setCustomerName("Test Customer");
        order.setDeliveryAddress("Room 101, College Hostel");
        order.setStatus("PLACED");
        order.setCreatedAt(LocalDateTime.now());
        order.setTotalAmount(238);

        order.addItem(new OrderItem(1L, "Classic Smash Burger", 149, 1));
        order.addItem(new OrderItem(4L, "Loaded French Fries", 89, 1));

        Path receiptPath = receiptService.generateReceipt(order);
        assertNotNull(receiptPath);
        assertTrue(Files.exists(receiptPath));
        assertEquals("receipt-order-42.txt", receiptPath.getFileName().toString());

        String content = receiptService.getReceiptContent(42L);
        assertNotNull(content);
        assertTrue(content.contains("Order ID         : #42"));
        assertTrue(content.contains("Customer Name    : Test Customer"));
        assertTrue(content.contains("Room 101, College Hostel"));
        assertTrue(content.contains("Classic Smash Burger"));
        assertTrue(content.contains("Loaded French Fries"));
        assertTrue(content.contains("₹     238"));
    }

    @Test
    void testReceiptIdempotency() {
        Order order = new Order();
        order.setId(10L);
        order.setCustomerName("Alice");
        order.setDeliveryAddress("Campus Hub");
        order.setTotalAmount(149);
        order.addItem(new OrderItem(1L, "Classic Smash Burger", 149, 1));

        Path firstPath = receiptService.generateReceipt(order);
        Path secondPath = receiptService.generateReceipt(order);

        assertEquals(firstPath, secondPath);
        assertTrue(receiptService.receiptExists(10L));
    }

    @Test
    void testMissingReceiptThrowsException() {
        assertThrows(ReceiptNotFoundException.class, () -> receiptService.getReceiptContent(9999L));
        assertThrows(ReceiptNotFoundException.class, () -> receiptService.getReceiptFilePath(9999L));
    }

    @Test
    void testInvalidOrderIdThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> receiptService.getReceiptContent(-1L));
        assertThrows(IllegalArgumentException.class, () -> receiptService.getReceiptContent(0L));
    }
}

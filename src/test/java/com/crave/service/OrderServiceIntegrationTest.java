package com.crave.service;

import com.crave.dto.OrderHistoryRecord;
import com.crave.dto.OrderRequest;
import com.crave.entity.Food;
import com.crave.entity.Order;
import com.crave.repository.FoodRepository;
import com.crave.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OrderServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private OrderRepository orderRepository;
    private FoodRepository foodRepository;
    private MenuFileService menuFileService;
    private FoodService foodService;
    private ReceiptService receiptService;
    private OrderHistoryFileService orderHistoryFileService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = Mockito.mock(OrderRepository.class);
        foodRepository = Mockito.mock(FoodRepository.class);

        Path receiptsDir = tempDir.resolve("receipts");
        Path historyFile = tempDir.resolve("order-history.jsonl");
        Path menuFile = tempDir.resolve("menu-overrides.json");

        ObjectMapper mapper = new ObjectMapper();
        receiptService = new ReceiptService(receiptsDir.toString());
        orderHistoryFileService = new OrderHistoryFileService(historyFile.toString(), mapper);
        menuFileService = new MenuFileService(menuFile.toString(), mapper);
        foodService = new FoodService(foodRepository, menuFileService);

        orderService = new OrderService(orderRepository, foodService, receiptService, orderHistoryFileService);

        // Mock baseline food item #1
        Food burger = new Food(1L, "Classic Smash Burger", "Burgers", "Desc", 149, "4.9", "BESTSELLER", "img", true);
        when(foodRepository.findById(1L)).thenReturn(Optional.of(burger));
        when(foodRepository.findAll()).thenReturn(List.of(burger));

        // Mock saved order returning an order with ID 99
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(99L);
            return o;
        });
    }

    @Test
    void testOrderCreationGeneratesReceiptAndFileHistory() {
        OrderRequest request = new OrderRequest();
        request.setCustomerName("Sanskar");
        request.setDeliveryAddress("Flat 202, Green Glen Layout");

        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest();
        itemReq.setFoodId(1L);
        itemReq.setQuantity(2);
        request.setItems(List.of(itemReq));

        Order placedOrder = orderService.createOrder(request);

        assertNotNull(placedOrder);
        assertEquals(99L, placedOrder.getId());
        assertEquals(298, placedOrder.getTotalAmount()); // 149 * 2

        // Verify receipt generated
        assertTrue(receiptService.receiptExists(99L));
        String receipt = receiptService.getReceiptContent(99L);
        assertTrue(receipt.contains("Classic Smash Burger"));
        assertTrue(receipt.contains("₹     298"));

        // Verify order history persisted in JSONL file
        List<OrderHistoryRecord> history = orderHistoryFileService.readOrderHistory();
        assertEquals(1, history.size());
        assertEquals(99L, history.get(0).getOrderId());
        assertEquals("Sanskar", history.get(0).getCustomerName());
    }

    @Test
    void testOrderFailsWhenItemIsOutOfStock() {
        // Mark burger as out of stock via menu overrides file
        menuFileService.updateAvailability(1L, false);

        OrderRequest request = new OrderRequest();
        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest();
        itemReq.setFoodId(1L);
        itemReq.setQuantity(1);
        request.setItems(List.of(itemReq));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
        assertTrue(ex.getMessage().contains("out of stock"));
    }

    @Test
    void testOrderUsesServerPriceOverrideNotFrontend() {
        // Update price in menu override file to ₹189
        menuFileService.updatePrice(1L, 189);

        OrderRequest request = new OrderRequest();
        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest();
        itemReq.setFoodId(1L);
        itemReq.setQuantity(2);
        request.setItems(List.of(itemReq));

        Order placedOrder = orderService.createOrder(request);
        assertEquals(378, placedOrder.getTotalAmount()); // 189 * 2
    }
}

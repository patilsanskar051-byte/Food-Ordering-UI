package com.crave.controller;

import com.crave.dto.OrderHistoryRecord;
import com.crave.entity.Food;
import com.crave.entity.Order;
import com.crave.entity.OrderItem;
import com.crave.exception.GlobalExceptionHandler;
import com.crave.exception.ReceiptNotFoundException;
import com.crave.repository.FoodRepository;
import com.crave.repository.OrderRepository;
import com.crave.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ControllerTests {

    @TempDir
    Path tempDir;

    private MockMvc receiptMockMvc;
    private MockMvc adminMockMvc;
    private MockMvc orderMockMvc;

    private ReceiptService receiptService;
    private OrderHistoryFileService orderHistoryFileService;
    private MenuFileService menuFileService;
    private FoodService foodService;
    private OrderService orderService;
    private FoodRepository foodRepository;
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        Path receiptsDir = tempDir.resolve("receipts");
        Path historyFile = tempDir.resolve("order-history.jsonl");
        Path menuFile = tempDir.resolve("menu-overrides.json");

        ObjectMapper mapper = new ObjectMapper();
        receiptService = new ReceiptService(receiptsDir.toString());
        orderHistoryFileService = new OrderHistoryFileService(historyFile.toString(), mapper);
        menuFileService = new MenuFileService(menuFile.toString(), mapper);

        foodRepository = Mockito.mock(FoodRepository.class);
        orderRepository = Mockito.mock(OrderRepository.class);

        foodService = new FoodService(foodRepository, menuFileService);
        orderService = new OrderService(orderRepository, foodService, receiptService, orderHistoryFileService);

        ReceiptController receiptController = new ReceiptController(receiptService);
        AdminMenuController adminController = new AdminMenuController(
                menuFileService, foodService, orderRepository, orderHistoryFileService, "crave-admin-secret-2026"
        );
        OrderController orderController = new OrderController(orderService, orderHistoryFileService);

        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

        receiptMockMvc = MockMvcBuilders.standaloneSetup(receiptController)
                .setControllerAdvice(exceptionHandler)
                .build();
        adminMockMvc = MockMvcBuilders.standaloneSetup(adminController)
                .setControllerAdvice(exceptionHandler)
                .build();
        orderMockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void testDownloadReceiptSuccess() throws Exception {
        Order order = new Order();
        order.setId(10L);
        order.setCustomerName("Test User");
        order.setDeliveryAddress("Campus Hostels");
        order.setTotalAmount(149);
        order.setCreatedAt(LocalDateTime.now());
        order.addItem(new OrderItem(1L, "Classic Smash Burger", 149, 1));

        receiptService.generateReceipt(order);

        receiptMockMvc.perform(get("/api/orders/10/receipt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"receipt-order-10.txt\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CRAVE FOOD ORDERING")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Order ID         : #10")));
    }

    @Test
    void testDownloadReceiptNotFound() throws Exception {
        receiptMockMvc.perform(get("/api/orders/999/receipt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Receipt Not Found"));
    }

    @Test
    void testAdminAccessDeniedWithoutToken() throws Exception {
        adminMockMvc.perform(get("/api/admin/menu/overrides"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access Denied"));
    }

    @Test
    void testAdminAccessDeniedWithInvalidToken() throws Exception {
        adminMockMvc.perform(get("/api/admin/menu/overrides")
                        .header("X-Admin-Token", "wrong-secret-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access Denied"));
    }

    @Test
    void testAdminVerifyWithCorrectToken() throws Exception {
        adminMockMvc.perform(post("/api/admin/verify")
                        .header("X-Admin-Token", "crave-admin-secret-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void testAdminUpdatePriceSuccess() throws Exception {
        Food burger = new Food(1L, "Classic Smash Burger", "Burgers", "Desc", 149, "4.9", "BEST", "img", true);
        when(foodRepository.findById(1L)).thenReturn(Optional.of(burger));
        when(foodRepository.findAll()).thenReturn(List.of(burger));

        adminMockMvc.perform(put("/api/admin/menu/items/1/price")
                        .header("X-Admin-Token", "crave-admin-secret-2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 179}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.food.price").value(179));
    }

    @Test
    void testAdminGetStatsSuccess() throws Exception {
        Order mockOrder = new Order();
        mockOrder.setId(1L);
        mockOrder.setTotalAmount(500);
        when(orderRepository.findAll()).thenReturn(List.of(mockOrder));

        Food burger = new Food(1L, "Classic Smash Burger", "Burgers", "Desc", 149, "4.9", "BEST", "img", true);
        when(foodRepository.findAll()).thenReturn(List.of(burger));

        adminMockMvc.perform(get("/api/admin/stats")
                        .header("X-Admin-Token", "crave-admin-secret-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.totalRevenue").value(500))
                .andExpect(jsonPath("$.totalMenuItems").value(1))
                .andExpect(jsonPath("$.outOfStockCount").value(0));
    }

    @Test
    void testAdminGetStatsUnauthorized() throws Exception {
        adminMockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access Denied"));
    }

    @Test
    void testAdminDeleteFoodItemSuccess() throws Exception {
        Food custom = new Food();
        custom.setName("Truffle Fries");
        custom.setCategory("Snacks");
        custom.setPrice(150);
        Food created = menuFileService.addCustomFood(custom, 9L);

        adminMockMvc.perform(delete("/api/admin/menu/items/" + created.getId())
                        .header("X-Admin-Token", "crave-admin-secret-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.wasCustom").value(true));
    }

    @Test
    void testAdminDeleteFoodItemUnauthorized() throws Exception {
        adminMockMvc.perform(delete("/api/admin/menu/items/10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access Denied"));
    }

    @Test
    void testGetFileOrderHistory() throws Exception {
        Order order = new Order();
        order.setId(101L);
        order.setCustomerName("Sanskar");
        order.setTotalAmount(250);
        order.setCreatedAt(LocalDateTime.now());
        order.addItem(new OrderItem(1L, "Classic Smash Burger", 149, 1));
        orderHistoryFileService.recordOrder(order);

        orderMockMvc.perform(get("/api/orders/history/file"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(101))
                .andExpect(jsonPath("$[0].customerName").value("Sanskar"));
    }
}

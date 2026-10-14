package com.crave.controller;

import com.crave.exception.ReceiptNotFoundException;
import com.crave.service.ReceiptService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * REST controller for safely downloading and previewing order receipts.
 * Protects against path injection and serves plain-text receipt files.
 */
@RestController
@RequestMapping
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    /**
     * GET /api/orders/{id}/receipt
     * Also mapped to /api/receipts/{id}
     *
     * Downloads the plain-text receipt file for the given order ID.
     */
    @GetMapping({"/api/orders/{id}/receipt", "/api/receipts/{id}"})
    public ResponseEntity<Resource> downloadReceipt(@PathVariable Long id) throws IOException {
        Path receiptPath = receiptService.getReceiptFilePath(id);

        byte[] receiptBytes = Files.readAllBytes(receiptPath);
        ByteArrayResource resource = new ByteArrayResource(receiptBytes);

        String fileName = "receipt-order-" + id + ".txt";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .contentLength(receiptBytes.length)
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    /**
     * GET /api/orders/{id}/receipt/preview
     * Also mapped to /api/receipts/{id}/preview
     *
     * Returns the receipt text content for in-browser modal viewing.
     */
    @GetMapping({"/api/orders/{id}/receipt/preview", "/api/receipts/{id}/preview"})
    public ResponseEntity<Map<String, Object>> previewReceipt(@PathVariable Long id) {
        String receiptContent = receiptService.getReceiptContent(id);
        return ResponseEntity.ok(Map.of(
                "orderId", id,
                "available", true,
                "content", receiptContent
        ));
    }
}

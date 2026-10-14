package com.crave.exception;

/**
 * Exception thrown when a requested order receipt cannot be found on disk.
 */
public class ReceiptNotFoundException extends RuntimeException {

    private final Long orderId;

    public ReceiptNotFoundException(Long orderId) {
        super("Receipt for order #" + orderId + " could not be found.");
        this.orderId = orderId;
    }

    public ReceiptNotFoundException(Long orderId, String message) {
        super(message);
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}

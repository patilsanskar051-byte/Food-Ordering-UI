package com.crave.exception;

/**
 * Exception thrown when menu item input fails business validation rules
 * (e.g., non-positive price, blank name, or invalid category).
 */
public class MenuValidationException extends RuntimeException {

    private final String fieldName;

    public MenuValidationException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}

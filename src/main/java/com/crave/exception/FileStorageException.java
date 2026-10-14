package com.crave.exception;

/**
 * Exception thrown when file I/O operations fail (e.g. storage error, directory creation failure).
 */
public class FileStorageException extends RuntimeException {

    private final String targetPath;

    public FileStorageException(String message, String targetPath, Throwable cause) {
        super(message, cause);
        this.targetPath = targetPath;
    }

    public FileStorageException(String message, String targetPath) {
        super(message);
        this.targetPath = targetPath;
    }

    public String getTargetPath() {
        return targetPath;
    }
}

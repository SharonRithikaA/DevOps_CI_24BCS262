package com.gymms.exception;

/** Raised when a record cannot be deleted because other records still depend on it (HTTP 409). */
public class ResourceInUseException extends RuntimeException {
    public ResourceInUseException(String message) {
        super(message);
    }
}

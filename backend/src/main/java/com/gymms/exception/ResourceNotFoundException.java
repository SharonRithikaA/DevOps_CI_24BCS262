package com.gymms.exception;

/** Base type for "entity does not exist" errors (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

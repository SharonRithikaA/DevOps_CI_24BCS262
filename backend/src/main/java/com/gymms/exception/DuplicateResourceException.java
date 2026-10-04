package com.gymms.exception;

/** Raised for unique-value clashes such as an e-mail or phone that is already registered (HTTP 409). */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}

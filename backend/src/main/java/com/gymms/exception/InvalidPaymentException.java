package com.gymms.exception;

/** Raised when a payment amount, discount or status transition is not valid (HTTP 400). */
public class InvalidPaymentException extends RuntimeException {
    public InvalidPaymentException(String message) {
        super(message);
    }
}

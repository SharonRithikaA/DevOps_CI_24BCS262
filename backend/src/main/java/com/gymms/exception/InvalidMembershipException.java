package com.gymms.exception;

/** Raised when membership dates, duration or plan data break a business rule (HTTP 400). */
public class InvalidMembershipException extends RuntimeException {
    public InvalidMembershipException(String message) {
        super(message);
    }
}

package com.gymms.exception;

/** Raised when a member is marked present twice on the same day (HTTP 409). */
public class DuplicateAttendanceException extends RuntimeException {
    public DuplicateAttendanceException(String message) {
        super(message);
    }
}

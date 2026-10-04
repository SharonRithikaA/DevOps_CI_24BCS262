package com.gymms.dto;

import java.time.Instant;
import java.util.Map;

/** Uniform error body returned by the GlobalExceptionHandler. */
public record ApiError(Instant timestamp, int status, String error, String message, Map<String, String> fieldErrors) {
    public static ApiError of(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }
}

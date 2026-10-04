package com.gymms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class PlanDto {
    private PlanDto() {
    }

    public record Request(
            @NotBlank(message = "Plan name is required")
            @Size(max = 60, message = "Plan name must be at most 60 characters") String name,
            @NotNull(message = "Duration is required")
            @Positive(message = "Duration must be at least 1 month")
            @Max(value = 60, message = "Duration cannot exceed 60 months") Integer durationMonths,
            @NotNull(message = "Price is required")
            @Positive(message = "Price must be greater than zero") BigDecimal price,
            @Size(max = 255, message = "Description must be at most 255 characters") String description,
            Boolean active) {
    }

    public record Response(Long id, String name, int durationMonths, BigDecimal price, String description,
                           boolean active, long memberCount) {
    }
}

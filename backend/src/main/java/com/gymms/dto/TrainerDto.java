package com.gymms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class TrainerDto {
    private TrainerDto() {
    }

    public record Request(
            @NotBlank(message = "Name is required")
            @Size(max = 100, message = "Name must be at most 100 characters") String name,
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be a valid address") String email,
            @NotBlank(message = "Phone is required")
            @Pattern(regexp = "^\\+?[0-9]{10,13}$", message = "Phone must contain 10 to 13 digits") String phone,
            @Size(max = 100, message = "Specialization must be at most 100 characters") String specialization,
            @NotNull(message = "Experience is required")
            @PositiveOrZero(message = "Experience cannot be negative")
            @Max(value = 60, message = "Experience looks too high") Integer experienceYears,
            @Size(max = 100, message = "Availability must be at most 100 characters") String availability,
            Boolean active) {
    }

    public record MemberSummary(Long id, String memberCode, String fullName) {
    }

    public record Response(Long id, String name, String email, String phone, String specialization,
                           int experienceYears, String availability, boolean active,
                           int assignedCount, List<MemberSummary> assignedMembers) {
    }

    public record AssignRequest(@NotNull(message = "memberIds is required") List<Long> memberIds) {
    }
}

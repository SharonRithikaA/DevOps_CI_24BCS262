package com.gymms.dto;

import com.gymms.entity.Gender;
import com.gymms.entity.MembershipStatus;
import com.gymms.entity.PaymentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class MemberDto {
    private MemberDto() {
    }

    /** Used for both registering and updating a member. */
    public record Request(
            @NotBlank(message = "Full name is required")
            @Size(max = 100, message = "Full name must be at most 100 characters") String fullName,
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be a valid address")
            @Size(max = 120, message = "Email must be at most 120 characters") String email,
            @NotBlank(message = "Phone is required")
            @Pattern(regexp = "^\\+?[0-9]{10,13}$", message = "Phone must contain 10 to 13 digits") String phone,
            @NotNull(message = "Date of birth is required")
            @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
            @NotNull(message = "Gender is required") Gender gender,
            @Size(max = 255, message = "Address must be at most 255 characters") String address,
            @NotBlank(message = "Emergency contact is required")
            @Size(max = 100, message = "Emergency contact must be at most 100 characters") String emergencyContact,
            @NotNull(message = "Membership plan is required") Long planId,
            LocalDate membershipStartDate,
            LocalDate membershipEndDate,
            Long trainerId) {
    }

    public record Response(
            Long id,
            String memberCode,
            String fullName,
            String email,
            String phone,
            LocalDate dateOfBirth,
            Gender gender,
            String address,
            String emergencyContact,
            LocalDate joinDate,
            Long planId,
            String planName,
            LocalDate membershipStartDate,
            LocalDate membershipEndDate,
            MembershipStatus membershipStatus,
            long remainingDays,
            Long trainerId,
            String trainerName,
            PaymentStatus paymentStatus) {
    }
}

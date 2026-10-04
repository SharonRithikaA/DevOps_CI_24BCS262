package com.gymms.dto;

import com.gymms.entity.PaymentMethod;
import com.gymms.entity.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PaymentDto {
    private PaymentDto() {
    }

    /** The base fee is always taken from the selected plan on the server; only the discount comes from the client. */
    public record Request(
            @NotNull(message = "Member is required") Long memberId,
            @NotNull(message = "Membership plan is required") Long planId,
            @DecimalMin(value = "0", message = "Discount cannot be negative") BigDecimal discountPercent,
            @NotNull(message = "Payment method is required") PaymentMethod method,
            PaymentStatus status,
            @Size(max = 40, message = "Transaction reference must be at most 40 characters") String transactionReference) {
    }

    public record StatusRequest(@NotNull(message = "Status is required") PaymentStatus status) {
    }

    public record Response(Long id, Long memberId, String memberCode, String memberName, Long planId,
                           String planName, BigDecimal amount, BigDecimal discountPercent,
                           BigDecimal discountAmount, BigDecimal finalAmount, PaymentMethod method,
                           PaymentStatus status, LocalDate paymentDate, String transactionReference) {
    }
}

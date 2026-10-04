package com.gymms.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public final class AttendanceDto {
    private AttendanceDto() {
    }

    public record Request(@NotNull(message = "Member is required") Long memberId) {
    }

    public record Response(Long id, Long memberId, String memberCode, String memberName, String planName,
                           LocalDate attendanceDate, LocalTime checkInTime) {
    }
}

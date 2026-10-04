package com.gymms.dto;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardDto {
    private DashboardDto() {
    }

    public record ChartPoint(String label, BigDecimal value) {
    }

    public record Response(
            long totalMembers,
            long activeMembers,
            long expiredMembers,
            long expiringSoon,
            long todayAttendance,
            BigDecimal monthlyRevenue,
            long activeTrainers,
            List<ChartPoint> monthlyRevenueChart,
            List<ChartPoint> planDistribution,
            List<ChartPoint> attendanceTrend,
            List<ChartPoint> statusBreakdown,
            List<MemberDto.Response> expiringMembers,
            List<PaymentDto.Response> recentPayments) {
    }
}

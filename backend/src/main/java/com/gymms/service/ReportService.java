package com.gymms.service;

import com.gymms.dto.ReportTable;
import com.gymms.entity.Attendance;
import com.gymms.entity.Member;
import com.gymms.entity.MembershipStatus;
import com.gymms.entity.Payment;
import com.gymms.entity.PaymentStatus;
import com.gymms.repository.AttendanceRepository;
import com.gymms.repository.MemberRepository;
import com.gymms.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the five reports (membership, active, expired, revenue, attendance) as generic tables,
 * which the API returns as JSON and as CSV.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final AttendanceRepository attendanceRepository;
    private final MemberService memberService;

    public ReportService(MemberRepository memberRepository, PaymentRepository paymentRepository,
                         AttendanceRepository attendanceRepository, MemberService memberService) {
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
        this.attendanceRepository = attendanceRepository;
        this.memberService = memberService;
    }

    public ReportTable build(String type, LocalDate from, LocalDate to, Long planId, PaymentStatus paymentStatus) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("The 'to' date cannot be before the 'from' date");
        }
        String t = type == null ? "" : type.trim().toLowerCase();
        return switch (t) {
            case "membership" -> membershipReport("Membership report", "ALL", from, to, planId, paymentStatus);
            case "active" -> membershipReport("Active membership report", "ACTIVE", from, to, planId, paymentStatus);
            case "expired" -> membershipReport("Expired membership report", "EXPIRED", from, to, planId, paymentStatus);
            case "revenue" -> revenueReport(from, to, planId, paymentStatus);
            case "attendance" -> attendanceReport(from, to, planId);
            default -> throw new IllegalArgumentException("Unknown report type: " + type);
        };
    }

    private ReportTable membershipReport(String title, String statusFilter, LocalDate from, LocalDate to,
                                         Long planId, PaymentStatus paymentStatus) {
        List<Member> members = memberRepository.findAll().stream()
                .filter(m -> planId == null || m.getPlan().getId().equals(planId))
                .filter(m -> paymentStatus == null || m.getPaymentStatus() == paymentStatus)
                .filter(m -> from == null || !m.getMembershipStartDate().isBefore(from))
                .filter(m -> to == null || !m.getMembershipStartDate().isAfter(to))
                .filter(m -> MemberService.matchesStatus(memberService.statusOf(m), statusFilter))
                .sorted(Comparator.comparing(Member::getMembershipEndDate))
                .toList();

        List<List<String>> rows = members.stream().map(m -> List.of(
                nz(m.getMemberCode()), m.getFullName(), m.getEmail(), m.getPhone(), m.getPlan().getName(),
                m.getMembershipStartDate().toString(), m.getMembershipEndDate().toString(),
                memberService.statusOf(m).name(), String.valueOf(memberService.toResponse(m).remainingDays()),
                m.getPaymentStatus().name())).toList();

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("Members in report", String.valueOf(members.size()));
        long expiringSoon = members.stream().filter(m -> memberService.statusOf(m) == MembershipStatus.EXPIRING_SOON).count();
        summary.put("Expiring within 7 days", String.valueOf(expiringSoon));
        return new ReportTable(title,
                List.of("Member ID", "Name", "Email", "Phone", "Plan", "Start Date", "End Date", "Status",
                        "Remaining Days", "Payment Status"),
                rows, summary);
    }

    private ReportTable revenueReport(LocalDate from, LocalDate to, Long planId, PaymentStatus paymentStatus) {
        List<Payment> payments = paymentRepository.findAllByOrderByPaymentDateDescIdDesc().stream()
                .filter(p -> planId == null || p.getPlan().getId().equals(planId))
                .filter(p -> paymentStatus == null || p.getStatus() == paymentStatus)
                .filter(p -> from == null || !p.getPaymentDate().isBefore(from))
                .filter(p -> to == null || !p.getPaymentDate().isAfter(to))
                .toList();

        List<List<String>> rows = payments.stream().map(p -> List.of(
                p.getPaymentDate().toString(), p.getTransactionReference(), p.getMember().getFullName(),
                p.getPlan().getName(), p.getMethod().name(), money(p.getAmount()),
                p.getDiscountPercent().stripTrailingZeros().toPlainString(), money(p.getFinalAmount()),
                p.getStatus().name())).toList();

        BigDecimal collected = sum(payments, PaymentStatus.PAID, false);
        BigDecimal discounts = sum(payments, PaymentStatus.PAID, true);
        BigDecimal pending = sum(payments, PaymentStatus.PENDING, false);
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("Transactions", String.valueOf(payments.size()));
        summary.put("Collected (Rs)", money(collected));
        summary.put("Pending (Rs)", money(pending));
        summary.put("Discounts given (Rs)", money(discounts));
        return new ReportTable("Revenue report",
                List.of("Date", "Transaction Ref", "Member", "Plan", "Method", "Base Amount (Rs)", "Discount (%)",
                        "Final Amount (Rs)", "Status"),
                rows, summary);
    }

    private ReportTable attendanceReport(LocalDate from, LocalDate to, Long planId) {
        List<Attendance> records = attendanceRepository.findAllByOrderByAttendanceDateDescCheckInTimeDesc().stream()
                .filter(a -> planId == null || a.getMember().getPlan().getId().equals(planId))
                .filter(a -> from == null || !a.getAttendanceDate().isBefore(from))
                .filter(a -> to == null || !a.getAttendanceDate().isAfter(to))
                .toList();

        List<List<String>> rows = records.stream().map(a -> List.of(
                a.getAttendanceDate().toString(), a.getCheckInTime().toString(), nz(a.getMember().getMemberCode()),
                a.getMember().getFullName(), a.getMember().getPlan().getName())).toList();

        Set<Long> unique = records.stream().map(a -> a.getMember().getId()).collect(Collectors.toSet());
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("Total check-ins", String.valueOf(records.size()));
        summary.put("Unique members", String.valueOf(unique.size()));
        return new ReportTable("Attendance report",
                List.of("Date", "Check-in Time", "Member ID", "Name", "Plan"), rows, summary);
    }

    private static BigDecimal sum(List<Payment> payments, PaymentStatus status, boolean discountOnly) {
        return payments.stream().filter(p -> p.getStatus() == status)
                .map(p -> discountOnly ? p.getAmount().subtract(p.getFinalAmount()) : p.getFinalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

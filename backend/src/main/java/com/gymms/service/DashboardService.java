package com.gymms.service;

import com.gymms.dto.DashboardDto;
import com.gymms.dto.DashboardDto.ChartPoint;
import com.gymms.entity.Attendance;
import com.gymms.entity.Member;
import com.gymms.entity.MembershipPlan;
import com.gymms.entity.MembershipStatus;
import com.gymms.entity.Payment;
import com.gymms.entity.PaymentStatus;
import com.gymms.repository.AttendanceRepository;
import com.gymms.repository.MemberRepository;
import com.gymms.repository.MembershipPlanRepository;
import com.gymms.repository.PaymentRepository;
import com.gymms.repository.TrainerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the numbers and chart series shown on the dashboard. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int REVENUE_MONTHS = 6;
    private static final int ATTENDANCE_DAYS = 14;

    private final MemberRepository memberRepository;
    private final TrainerRepository trainerRepository;
    private final PaymentRepository paymentRepository;
    private final AttendanceRepository attendanceRepository;
    private final MembershipPlanRepository planRepository;
    private final MembershipService membershipService;
    private final MemberService memberService;
    private final PaymentService paymentService;

    public DashboardService(MemberRepository memberRepository, TrainerRepository trainerRepository,
                            PaymentRepository paymentRepository, AttendanceRepository attendanceRepository,
                            MembershipPlanRepository planRepository, MembershipService membershipService,
                            MemberService memberService, PaymentService paymentService) {
        this.memberRepository = memberRepository;
        this.trainerRepository = trainerRepository;
        this.paymentRepository = paymentRepository;
        this.attendanceRepository = attendanceRepository;
        this.planRepository = planRepository;
        this.membershipService = membershipService;
        this.memberService = memberService;
        this.paymentService = paymentService;
    }

    public DashboardDto.Response getDashboard() {
        LocalDate today = membershipService.today();
        List<Member> members = memberRepository.findAll();

        Map<MembershipStatus, Long> counts = new EnumMap<>(MembershipStatus.class);
        for (Member m : members) {
            counts.merge(memberService.statusOf(m), 1L, Long::sum);
        }
        long soon = counts.getOrDefault(MembershipStatus.EXPIRING_SOON, 0L);
        long active = counts.getOrDefault(MembershipStatus.ACTIVE, 0L) + soon;
        long expired = counts.getOrDefault(MembershipStatus.EXPIRED, 0L);

        List<Payment> allPayments = paymentRepository.findAllByOrderByPaymentDateDescIdDesc();
        Map<YearMonth, BigDecimal> revenueByMonth = new HashMap<>();
        for (Payment p : allPayments) {
            if (p.getStatus() == PaymentStatus.PAID) {
                revenueByMonth.merge(YearMonth.from(p.getPaymentDate()), p.getFinalAmount(), BigDecimal::add);
            }
        }
        YearMonth thisMonth = YearMonth.from(today);
        List<ChartPoint> revenueChart = new ArrayList<>();
        for (int i = REVENUE_MONTHS - 1; i >= 0; i--) {
            YearMonth ym = thisMonth.minusMonths(i);
            String label = ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            revenueChart.add(new ChartPoint(label, revenueByMonth.getOrDefault(ym, BigDecimal.ZERO)));
        }

        List<ChartPoint> planChart = new ArrayList<>();
        for (MembershipPlan plan : planRepository.findAllByOrderByDurationMonthsAsc()) {
            long n = members.stream().filter(m -> m.getPlan().getId().equals(plan.getId())).count();
            planChart.add(new ChartPoint(plan.getName(), BigDecimal.valueOf(n)));
        }

        Map<LocalDate, Long> perDay = new HashMap<>();
        for (Attendance a : attendanceRepository.findByAttendanceDateBetween(today.minusDays(ATTENDANCE_DAYS - 1L), today)) {
            perDay.merge(a.getAttendanceDate(), 1L, Long::sum);
        }
        DateTimeFormatter dayLabel = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
        List<ChartPoint> attendanceChart = new ArrayList<>();
        for (int i = ATTENDANCE_DAYS - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            attendanceChart.add(new ChartPoint(d.format(dayLabel), BigDecimal.valueOf(perDay.getOrDefault(d, 0L))));
        }

        List<ChartPoint> statusChart = new ArrayList<>();
        statusChart.add(new ChartPoint("Active", BigDecimal.valueOf(counts.getOrDefault(MembershipStatus.ACTIVE, 0L))));
        statusChart.add(new ChartPoint("Expiring soon", BigDecimal.valueOf(soon)));
        statusChart.add(new ChartPoint("Expired", BigDecimal.valueOf(expired)));

        return new DashboardDto.Response(
                members.size(), active, expired, soon,
                attendanceRepository.countByAttendanceDate(today),
                revenueByMonth.getOrDefault(thisMonth, BigDecimal.ZERO),
                trainerRepository.countByActiveTrue(),
                revenueChart, planChart, attendanceChart, statusChart,
                members.stream()
                        .filter(m -> memberService.statusOf(m) == MembershipStatus.EXPIRING_SOON)
                        .sorted(Comparator.comparing(Member::getMembershipEndDate))
                        .limit(5).map(memberService::toResponse).toList(),
                allPayments.stream().limit(5).map(paymentService::toResponse).toList());
    }
}

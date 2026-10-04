package com.gymms.config;

import com.gymms.entity.AppUser;
import com.gymms.entity.Attendance;
import com.gymms.entity.Gender;
import com.gymms.entity.Member;
import com.gymms.entity.MembershipPlan;
import com.gymms.entity.Payment;
import com.gymms.entity.PaymentMethod;
import com.gymms.entity.PaymentStatus;
import com.gymms.entity.Role;
import com.gymms.entity.Trainer;
import com.gymms.repository.AppUserRepository;
import com.gymms.repository.AttendanceRepository;
import com.gymms.repository.MemberRepository;
import com.gymms.repository.MembershipPlanRepository;
import com.gymms.repository.PaymentRepository;
import com.gymms.repository.TrainerRepository;
import com.gymms.service.MembershipService;
import com.gymms.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Start-up data.
 * <ul>
 *   <li>Always: creates the demo login users (admin, staff) when no users exist.</li>
 *   <li>When {@code app.seed.enabled=true} and the database is empty: loads clearly-marked DEMO data
 *       (plans, trainers, members using @example.com addresses, payments with DEMO- references and
 *       attendance) with dates relative to today so the dashboard is never empty.</li>
 * </ul>
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private record MemberRow(String name, Gender gender, int planIndex, int daysAgo, int trainerIndex, boolean paid) {
    }

    private static final List<MemberRow> MEMBERS = List.of(
            new MemberRow("Aarav Sharma", Gender.MALE, 0, 3, 0, true),
            new MemberRow("Priya Nair", Gender.FEMALE, 0, 26, 1, true),
            new MemberRow("Rohan Mehta", Gender.MALE, 0, 45, 0, true),
            new MemberRow("Ananya Iyer", Gender.FEMALE, 0, 12, 2, true),
            new MemberRow("Karthik Subramanian", Gender.MALE, 1, 20, 0, true),
            new MemberRow("Divya Krishnan", Gender.FEMALE, 1, 60, 1, true),
            new MemberRow("Vikram Singh", Gender.MALE, 1, 86, 3, true),
            new MemberRow("Sneha Reddy", Gender.FEMALE, 1, 110, 2, true),
            new MemberRow("Arjun Patel", Gender.MALE, 1, 30, 1, false),
            new MemberRow("Meera Pillai", Gender.FEMALE, 2, 10, 3, true),
            new MemberRow("Rahul Verma", Gender.MALE, 2, 90, 0, true),
            new MemberRow("Kavya Menon", Gender.FEMALE, 2, 178, 2, true),
            new MemberRow("Siddharth Rao", Gender.MALE, 2, 200, 1, true),
            new MemberRow("Isha Gupta", Gender.FEMALE, 2, 45, 0, false),
            new MemberRow("Naveen Kumar", Gender.MALE, 3, 15, 2, true),
            new MemberRow("Lakshmi Narayanan", Gender.FEMALE, 3, 120, 3, true),
            new MemberRow("Harish Babu", Gender.MALE, 3, 300, 1, true),
            new MemberRow("Pooja Desai", Gender.FEMALE, 3, 360, 0, true),
            new MemberRow("Manoj Kumar", Gender.MALE, 3, 400, -1, true),
            new MemberRow("Aishwarya Raj", Gender.FEMALE, 0, 8, 2, true));

    private static final String[] ADDRESSES = {
            "12, Anna Nagar, Chennai", "45, RS Puram, Coimbatore", "7, Indiranagar, Bengaluru",
            "23, Adyar, Chennai", "88, Gandhipuram, Coimbatore"};

    private static final BigDecimal[] DISCOUNTS = {
            BigDecimal.ZERO, BigDecimal.TEN, new BigDecimal("5"), BigDecimal.ZERO};

    private final AppUserRepository userRepository;
    private final MembershipPlanRepository planRepository;
    private final TrainerRepository trainerRepository;
    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final AttendanceRepository attendanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final MembershipService membershipService;
    private final PaymentService paymentService;
    private final boolean seedSampleData;
    private final String adminPassword;
    private final String staffPassword;

    public DataSeeder(AppUserRepository userRepository, MembershipPlanRepository planRepository,
                      TrainerRepository trainerRepository, MemberRepository memberRepository,
                      PaymentRepository paymentRepository, AttendanceRepository attendanceRepository,
                      PasswordEncoder passwordEncoder, MembershipService membershipService,
                      PaymentService paymentService,
                      @Value("${app.seed.enabled}") boolean seedSampleData,
                      @Value("${app.seed.admin-password}") String adminPassword,
                      @Value("${app.seed.staff-password}") String staffPassword) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.trainerRepository = trainerRepository;
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
        this.attendanceRepository = attendanceRepository;
        this.passwordEncoder = passwordEncoder;
        this.membershipService = membershipService;
        this.paymentService = paymentService;
        this.seedSampleData = seedSampleData;
        this.adminPassword = adminPassword;
        this.staffPassword = staffPassword;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        if (seedSampleData && planRepository.count() == 0 && memberRepository.count() == 0) {
            seedSampleData();
        }
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            return;
        }
        userRepository.save(user("admin", adminPassword, "Gym Administrator", Role.ADMIN));
        userRepository.save(user("staff", staffPassword, "Front Desk Staff", Role.STAFF));
        log.info("Created demo users 'admin' and 'staff'. Change these passwords for any real deployment.");
    }

    private AppUser user(String username, String rawPassword, String fullName, Role role) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setFullName(fullName);
        u.setRole(role);
        return u;
    }

    private void seedSampleData() {
        LocalDate today = membershipService.today();

        List<MembershipPlan> plans = List.of(
                plan("Basic", 1, "1200", "Gym floor access, 1 month"),
                plan("Standard", 3, "3000", "Gym floor + group classes, 3 months"),
                plan("Premium", 6, "5500", "All classes + trainer guidance, 6 months"),
                plan("Annual", 12, "9600", "Best value: full access for 12 months"));
        plans = planRepository.saveAll(plans);

        List<Trainer> trainers = trainerRepository.saveAll(List.of(
                trainer("Rajesh Kumar", "Strength & Conditioning", 8, "Mon-Sat, 6 AM - 2 PM", true),
                trainer("Sunita Rao", "Yoga & Mobility", 6, "Mon-Fri, 7 AM - 1 PM", true),
                trainer("Dinesh Babu", "CrossFit", 5, "Tue-Sun, 4 PM - 10 PM", true),
                trainer("Farah Khan", "Cardio & HIIT", 4, "Mon-Sat, 5 PM - 9 PM", true),
                trainer("Anil Menon", "Nutrition & Weight Loss", 10, "On leave", false)));

        List<Member> members = new ArrayList<>();
        List<Payment> payments = new ArrayList<>();
        int refCounter = 1;

        for (int i = 0; i < MEMBERS.size(); i++) {
            MemberRow row = MEMBERS.get(i);
            MembershipPlan plan = plans.get(row.planIndex());
            LocalDate start = today.minusDays(row.daysAgo());

            Member m = new Member();
            m.setFullName(row.name());
            m.setEmail(row.name().toLowerCase().replace(" ", ".") + "@example.com");
            m.setPhone("9840" + String.format("%06d", 100000 + i * 7919));
            m.setDateOfBirth(LocalDate.of(1985 + (i * 3) % 15, 1 + (i * 5) % 12, 1 + (i * 7) % 27));
            m.setGender(row.gender());
            m.setAddress(ADDRESSES[i % ADDRESSES.length]);
            m.setEmergencyContact("Sample Contact - 9" + String.format("%09d", 500000000 + i * 1237));
            m.setJoinDate(start);
            m.setPlan(plan);
            m.setMembershipStartDate(start);
            m.setMembershipEndDate(membershipService.calculateExpiryDate(start, plan.getDurationMonths()));
            m.setTrainer(row.trainerIndex() >= 0 ? trainers.get(row.trainerIndex()) : null);
            m.setPaymentStatus(row.paid() ? PaymentStatus.PAID : PaymentStatus.PENDING);
            m = memberRepository.save(m);
            m.setMemberCode(String.format("GYM-%04d", m.getId()));
            m = memberRepository.save(m);
            members.add(m);

            payments.add(payment(m, plan, start, DISCOUNTS[i % DISCOUNTS.length], PaymentMethod.values()[i % 4],
                    row.paid() ? PaymentStatus.PAID : PaymentStatus.PENDING, refCounter++));
            if (row.daysAgo() >= 100) { // earlier renewal of the same plan, to give the revenue chart some history
                payments.add(payment(m, plan, start.minusMonths(plan.getDurationMonths()), BigDecimal.ZERO,
                        PaymentMethod.values()[(i + 1) % 4], PaymentStatus.PAID, refCounter++));
            }
        }
        paymentRepository.saveAll(payments);

        Random random = new Random(42);
        List<Attendance> attendance = new ArrayList<>();
        for (int d = 0; d < 30; d++) {
            LocalDate day = today.minusDays(d);
            if (d > 0 && day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            for (Member m : members) {
                boolean valid = !day.isBefore(m.getMembershipStartDate()) && !day.isAfter(m.getMembershipEndDate());
                if (valid && random.nextDouble() < (d == 0 ? 0.55 : 0.4)) {
                    Attendance a = new Attendance();
                    a.setMember(m);
                    a.setAttendanceDate(day);
                    a.setCheckInTime(LocalTime.of(6 + random.nextInt(14), random.nextInt(60)));
                    attendance.add(a);
                }
            }
        }
        attendanceRepository.saveAll(attendance);
        log.info("Loaded DEMO sample data: {} plans, {} trainers, {} members, {} payments, {} attendance records.",
                plans.size(), trainers.size(), members.size(), payments.size(), attendance.size());
    }

    private MembershipPlan plan(String name, int months, String price, String description) {
        MembershipPlan p = new MembershipPlan();
        p.setName(name);
        p.setDurationMonths(months);
        p.setPrice(new BigDecimal(price));
        p.setDescription(description);
        p.setActive(true);
        return p;
    }

    private Trainer trainer(String name, String specialization, int years, String availability, boolean active) {
        Trainer t = new Trainer();
        t.setName(name);
        t.setEmail(name.toLowerCase().replace(" ", ".") + "@example.com");
        t.setPhone("98400" + String.format("%05d", 20000 + name.length() * 311));
        t.setSpecialization(specialization);
        t.setExperienceYears(years);
        t.setAvailability(availability);
        t.setActive(active);
        return t;
    }

    private Payment payment(Member member, MembershipPlan plan, LocalDate date, BigDecimal discount,
                            PaymentMethod method, PaymentStatus status, int refNumber) {
        Payment p = new Payment();
        p.setMember(member);
        p.setPlan(plan);
        p.setAmount(plan.getPrice());
        p.setDiscountPercent(discount);
        p.setFinalAmount(paymentService.calculateFinalAmount(plan.getPrice(), discount));
        p.setMethod(method);
        p.setStatus(status);
        p.setPaymentDate(date);
        p.setTransactionReference(String.format("DEMO-%04d", refNumber));
        return p;
    }
}

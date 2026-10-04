package com.gymms.service;

import com.gymms.dto.PaymentDto;
import com.gymms.entity.Member;
import com.gymms.entity.MembershipPlan;
import com.gymms.entity.Payment;
import com.gymms.entity.PaymentMethod;
import com.gymms.entity.PaymentStatus;
import com.gymms.exception.InvalidPaymentException;
import com.gymms.exception.ResourceNotFoundException;
import com.gymms.repository.MemberRepository;
import com.gymms.repository.MembershipPlanRepository;
import com.gymms.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Payment rules: fee, discount and final-amount calculation, amount validation,
 * status checks, and applying a settled payment to the member's membership.
 */
@Service
@Transactional
public class PaymentService {

    /** Highest discount staff may give, in percent. Single source of truth for this business rule. */
    public static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("50");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final MembershipPlanRepository planRepository;
    private final MemberService memberService;
    private final MembershipService membershipService;

    public PaymentService(PaymentRepository paymentRepository, MemberRepository memberRepository,
                          MembershipPlanRepository planRepository, MemberService memberService,
                          MembershipService membershipService) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.memberService = memberService;
        this.membershipService = membershipService;
    }

    // ------------------------------------------------------------ pure business logic

    /** A payment amount must be present and greater than zero. */
    public void validatePaymentAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidPaymentException("Payment amount is required");
        }
        if (amount.signum() <= 0) {
            throw new InvalidPaymentException("Payment amount must be greater than zero");
        }
    }

    /** null means "no discount"; otherwise the value must be between 0 and {@link #MAX_DISCOUNT_PERCENT}. */
    public BigDecimal normalizeDiscount(BigDecimal discountPercent) {
        if (discountPercent == null) {
            return BigDecimal.ZERO;
        }
        if (discountPercent.signum() < 0) {
            throw new InvalidPaymentException("Discount cannot be negative");
        }
        if (discountPercent.compareTo(MAX_DISCOUNT_PERCENT) > 0) {
            throw new InvalidPaymentException("Discount cannot exceed " + MAX_DISCOUNT_PERCENT.stripTrailingZeros().toPlainString() + "%");
        }
        return discountPercent;
    }

    /** Rupee value of the discount, rounded half-up to 2 decimals. Example: 3000 at 10% = 300.00 */
    public BigDecimal calculateDiscountAmount(BigDecimal baseFee, BigDecimal discountPercent) {
        validatePaymentAmount(baseFee);
        BigDecimal pct = normalizeDiscount(discountPercent);
        return baseFee.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Base fee minus discount. Example: 3000 at 10% = 2700.00. The result must still be positive. */
    public BigDecimal calculateFinalAmount(BigDecimal baseFee, BigDecimal discountPercent) {
        BigDecimal discount = calculateDiscountAmount(baseFee, discountPercent);
        BigDecimal result = baseFee.setScale(2, RoundingMode.HALF_UP).subtract(discount);
        validatePaymentAmount(result);
        return result;
    }

    public boolean isPaymentComplete(Payment payment) {
        return payment != null && payment.getStatus() == PaymentStatus.PAID;
    }

    public boolean isPaymentPending(Payment payment) {
        return payment != null && payment.getStatus() == PaymentStatus.PENDING;
    }

    public String generateTransactionReference(LocalDate date) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "TXN-" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + suffix;
    }

    // ------------------------------------------------------------ use cases

    public PaymentDto.Response recordPayment(PaymentDto.Request request) {
        Member member = memberService.findMemberEntity(request.memberId());
        MembershipPlan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found with id " + request.planId()));

        BigDecimal baseFee = membershipService.calculateMembershipFee(plan);
        BigDecimal discount = normalizeDiscount(request.discountPercent());
        BigDecimal finalAmount = calculateFinalAmount(baseFee, discount);
        PaymentStatus status = request.status() == null ? PaymentStatus.PAID : request.status();

        Payment payment = new Payment();
        payment.setMember(member);
        payment.setPlan(plan);
        payment.setAmount(baseFee);
        payment.setDiscountPercent(discount);
        payment.setFinalAmount(finalAmount);
        payment.setMethod(request.method());
        payment.setStatus(status);
        payment.setPaymentDate(membershipService.today());
        payment.setTransactionReference(resolveReference(request.transactionReference()));

        Payment saved = paymentRepository.save(payment);
        if (status == PaymentStatus.PAID) {
            applyPaidPayment(member, plan);
        }
        return toResponse(saved);
    }

    /** Settles (PAID) or rejects (FAILED) a payment that was recorded as PENDING. */
    public PaymentDto.Response updateStatus(Long paymentId, PaymentStatus newStatus) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id " + paymentId));
        if (!isPaymentPending(payment)) {
            throw new InvalidPaymentException("Only pending payments can be updated");
        }
        if (newStatus == null || newStatus == PaymentStatus.PENDING) {
            throw new InvalidPaymentException("New status must be PAID or FAILED");
        }
        payment.setStatus(newStatus);
        Payment saved = paymentRepository.save(payment);
        if (newStatus == PaymentStatus.PAID) {
            applyPaidPayment(payment.getMember(), payment.getPlan());
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto.Response> list(String query, PaymentStatus status, PaymentMethod method,
                                          LocalDate from, LocalDate to) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return paymentRepository.findAllByOrderByPaymentDateDescIdDesc().stream()
                .filter(p -> status == null || p.getStatus() == status)
                .filter(p -> method == null || p.getMethod() == method)
                .filter(p -> from == null || !p.getPaymentDate().isBefore(from))
                .filter(p -> to == null || !p.getPaymentDate().isAfter(to))
                .filter(p -> q.isEmpty()
                        || p.getMember().getFullName().toLowerCase().contains(q)
                        || (p.getMember().getMemberCode() != null && p.getMember().getMemberCode().toLowerCase().contains(q))
                        || p.getTransactionReference().toLowerCase().contains(q))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentDto.Response> listForMember(Long memberId) {
        memberService.findMemberEntity(memberId);
        return paymentRepository.findByMemberIdOrderByPaymentDateDescIdDesc(memberId).stream()
                .map(this::toResponse).toList();
    }

    public PaymentDto.Response toResponse(Payment p) {
        return new PaymentDto.Response(p.getId(), p.getMember().getId(), p.getMember().getMemberCode(),
                p.getMember().getFullName(), p.getPlan().getId(), p.getPlan().getName(), p.getAmount(),
                p.getDiscountPercent(), p.getAmount().subtract(p.getFinalAmount()), p.getFinalAmount(),
                p.getMethod(), p.getStatus(), p.getPaymentDate(), p.getTransactionReference());
    }

    // ------------------------------------------------------------ helpers

    /**
     * A paid payment either settles the member's current (unpaid) period, or renews the membership.
     * Renewal continues after the current end date, or starts today when the membership has lapsed.
     */
    void applyPaidPayment(Member member, MembershipPlan plan) {
        boolean settlesCurrentPeriod = member.getPaymentStatus() != PaymentStatus.PAID
                && member.getPlan() != null
                && member.getPlan().getId().equals(plan.getId())
                && !membershipService.isMembershipExpired(member.getMembershipEndDate());
        if (!settlesCurrentPeriod) {
            LocalDate start = membershipService.calculateRenewalStart(member.getMembershipEndDate());
            member.setPlan(plan);
            member.setMembershipStartDate(start);
            member.setMembershipEndDate(membershipService.calculateExpiryDate(start, plan.getDurationMonths()));
        }
        member.setPaymentStatus(PaymentStatus.PAID);
        memberRepository.save(member);
    }

    private String resolveReference(String requested) {
        if (requested == null || requested.isBlank()) {
            return generateTransactionReference(membershipService.today());
        }
        String ref = requested.trim();
        if (paymentRepository.existsByTransactionReference(ref)) {
            throw new InvalidPaymentException("Transaction reference '" + ref + "' has already been used");
        }
        return ref;
    }
}

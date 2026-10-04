package com.gymms.service;

import com.gymms.dto.PaymentDto;
import com.gymms.entity.Member;
import com.gymms.entity.MembershipPlan;
import com.gymms.entity.Payment;
import com.gymms.entity.PaymentMethod;
import com.gymms.entity.PaymentStatus;
import com.gymms.exception.InvalidPaymentException;
import com.gymms.exception.MemberNotFoundException;
import com.gymms.exception.ResourceNotFoundException;
import com.gymms.repository.MemberRepository;
import com.gymms.repository.MembershipPlanRepository;
import com.gymms.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static com.gymms.service.TestData.TODAY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PaymentService")
class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private MemberRepository memberRepository;
    private MembershipPlanRepository planRepository;
    private MemberService memberService;
    private PaymentService service;
    private MembershipPlan standard;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        memberRepository = mock(MemberRepository.class);
        planRepository = mock(MembershipPlanRepository.class);
        memberService = mock(MemberService.class);
        service = new PaymentService(paymentRepository, memberRepository, planRepository, memberService,
                new MembershipService(TestData.CLOCK));

        standard = TestData.standardPlan(); // Rs 3000, 3 months
        when(planRepository.findById(1L)).thenReturn(Optional.of(standard));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private PaymentDto.Request request(Long memberId, String discount, PaymentStatus status, String reference) {
        return new PaymentDto.Request(memberId, 1L, discount == null ? null : bd(discount), PaymentMethod.UPI,
                status, reference);
    }

    // ------------------------------------------------------------------ final amount / discount

    @Test
    @DisplayName("base fee 3000 with 10% discount gives a final amount of 2700.00")
    void tenPercentDiscount() {
        assertEquals(bd("2700.00"), service.calculateFinalAmount(bd("3000"), bd("10")));
        assertEquals(bd("300.00"), service.calculateDiscountAmount(bd("3000"), bd("10")));
    }

    @Test
    @DisplayName("no discount (0 or null) leaves the fee unchanged")
    void noDiscount() {
        assertEquals(bd("3000.00"), service.calculateFinalAmount(bd("3000"), BigDecimal.ZERO));
        assertEquals(bd("3000.00"), service.calculateFinalAmount(bd("3000"), null));
    }

    @Test
    @DisplayName("the maximum discount of 50% is allowed")
    void maximumDiscountBoundary() {
        assertEquals(bd("1500.00"), service.calculateFinalAmount(bd("3000"), bd("50")));
    }

    @ParameterizedTest(name = "discount {0}% is rejected")
    @ValueSource(strings = {"50.01", "75", "100", "-1", "-0.5"})
    void discountOutOfRange(String pct) {
        assertThrows(InvalidPaymentException.class, () -> service.calculateFinalAmount(bd("3000"), bd(pct)));
    }

    @ParameterizedTest(name = "{0} at {1}% -> discount {2}, final {3}")
    @CsvSource({
            "999.99, 12.5, 125.00, 874.99",   // 124.99875 rounds half-up to 125.00
            "1200, 5, 60.00, 1140.00",
            "5500, 33.33, 1833.15, 3666.85"
    })
    void roundingIsHalfUpToTwoDecimals(String base, String pct, String discount, String finalAmount) {
        assertEquals(bd(discount), service.calculateDiscountAmount(bd(base), bd(pct)));
        assertEquals(bd(finalAmount), service.calculateFinalAmount(bd(base), bd(pct)));
    }

    @Test
    @DisplayName("zero, negative or missing base fee is rejected")
    void invalidBaseFee() {
        assertThrows(InvalidPaymentException.class, () -> service.calculateFinalAmount(BigDecimal.ZERO, bd("10")));
        assertThrows(InvalidPaymentException.class, () -> service.calculateFinalAmount(bd("-100"), bd("10")));
        assertThrows(InvalidPaymentException.class, () -> service.calculateFinalAmount(null, bd("10")));
    }

    // ------------------------------------------------------------------ validation / status

    @Test
    @DisplayName("validatePaymentAmount accepts positive amounts, rejects null / zero / negative")
    void validateAmount() {
        assertDoesNotThrow(() -> service.validatePaymentAmount(bd("0.01")));
        assertThrows(InvalidPaymentException.class, () -> service.validatePaymentAmount(null));
        assertThrows(InvalidPaymentException.class, () -> service.validatePaymentAmount(BigDecimal.ZERO));
        assertThrows(InvalidPaymentException.class, () -> service.validatePaymentAmount(bd("-5")));
    }

    @Test
    @DisplayName("isPaymentComplete is true only for PAID payments")
    void paymentStatusChecks() {
        Payment p = new Payment();
        p.setStatus(PaymentStatus.PAID);
        assertTrue(service.isPaymentComplete(p));
        p.setStatus(PaymentStatus.PENDING);
        assertFalse(service.isPaymentComplete(p));
        assertTrue(service.isPaymentPending(p));
        p.setStatus(PaymentStatus.FAILED);
        assertFalse(service.isPaymentComplete(p));
        assertFalse(service.isPaymentComplete(null));
    }

    @Test
    @DisplayName("generated transaction references follow TXN-yyyyMMdd-XXXXXXXX")
    void transactionReferenceFormat() {
        String ref = service.generateTransactionReference(TODAY);
        assertTrue(ref.startsWith("TXN-20260315-"));
        assertEquals(21, ref.length());
        assertFalse(ref.equals(service.generateTransactionReference(TODAY)));
    }

    // ------------------------------------------------------------------ recordPayment

    @Test
    @DisplayName("recording a paid payment for a new (unpaid) member settles the current period")
    void recordPaymentSettlesCurrentPeriod() {
        Member m = TestData.member(1L, "Asha Active", standard, TODAY, TODAY.plusMonths(3).minusDays(1));
        m.setPaymentStatus(PaymentStatus.PENDING);
        when(memberService.findMemberEntity(1L)).thenReturn(m);

        PaymentDto.Response r = service.recordPayment(request(1L, "10", null, null));

        assertEquals(bd("3000.00"), r.amount());
        assertEquals(bd("300.00"), r.discountAmount());
        assertEquals(bd("2700.00"), r.finalAmount());
        assertEquals(PaymentStatus.PAID, r.status());
        assertEquals(TODAY, r.paymentDate());
        assertNotNull(r.transactionReference());
        assertEquals(PaymentStatus.PAID, m.getPaymentStatus());
        assertEquals(TODAY, m.getMembershipStartDate());                     // dates unchanged
        assertEquals(TODAY.plusMonths(3).minusDays(1), m.getMembershipEndDate());
        verify(memberRepository).save(m);
    }

    @Test
    @DisplayName("renewing an active, already-paid membership extends it from the day after the end date")
    void recordPaymentRenewsActiveMembership() {
        Member m = TestData.member(1L, "Asha Active", standard, TODAY.minusDays(60), TODAY.plusDays(30));
        when(memberService.findMemberEntity(1L)).thenReturn(m);

        service.recordPayment(request(1L, null, PaymentStatus.PAID, "UPI-REF-1"));

        assertEquals(TODAY.plusDays(31), m.getMembershipStartDate());
        assertEquals(TODAY.plusDays(31).plusMonths(3).minusDays(1), m.getMembershipEndDate());
        assertEquals(PaymentStatus.PAID, m.getPaymentStatus());
    }

    @Test
    @DisplayName("renewing a lapsed membership restarts it today")
    void recordPaymentRenewsLapsedMembership() {
        Member m = TestData.member(1L, "Ben Expired", standard, TODAY.minusDays(100), TODAY.minusDays(10));
        m.setPaymentStatus(PaymentStatus.PENDING);
        when(memberService.findMemberEntity(1L)).thenReturn(m);

        service.recordPayment(request(1L, null, null, null));

        assertEquals(TODAY, m.getMembershipStartDate());
        assertEquals(LocalDate.of(2026, 6, 14), m.getMembershipEndDate());
    }

    @Test
    @DisplayName("a PENDING payment does not change the member's membership")
    void pendingPaymentDoesNotTouchMember() {
        Member m = TestData.member(1L, "Asha Active", standard, TODAY.minusDays(60), TODAY.plusDays(30));
        when(memberService.findMemberEntity(1L)).thenReturn(m);

        PaymentDto.Response r = service.recordPayment(request(1L, null, PaymentStatus.PENDING, null));

        assertEquals(PaymentStatus.PENDING, r.status());
        verify(memberRepository, never()).save(any(Member.class));
        assertEquals(TODAY.plusDays(30), m.getMembershipEndDate());
    }

    @Test
    @DisplayName("rejects a discount above the allowed maximum and saves nothing")
    void recordPaymentExcessiveDiscount() {
        Member m = TestData.member(1L, "Asha Active", standard, TODAY.minusDays(60), TODAY.plusDays(30));
        when(memberService.findMemberEntity(1L)).thenReturn(m);

        assertThrows(InvalidPaymentException.class, () -> service.recordPayment(request(1L, "60", null, null)));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("rejects a transaction reference that was already used")
    void recordPaymentDuplicateReference() {
        Member m = TestData.member(1L, "Asha Active", standard, TODAY.minusDays(60), TODAY.plusDays(30));
        when(memberService.findMemberEntity(1L)).thenReturn(m);
        when(paymentRepository.existsByTransactionReference("REF-1")).thenReturn(true);

        assertThrows(InvalidPaymentException.class, () -> service.recordPayment(request(1L, null, null, "REF-1")));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("rejects an inactive plan, an unknown plan and an unknown member")
    void recordPaymentInvalidReferences() {
        Member m = TestData.member(1L, "Asha Active", standard, TODAY.minusDays(60), TODAY.plusDays(30));
        when(memberService.findMemberEntity(1L)).thenReturn(m);
        when(planRepository.findById(2L)).thenReturn(Optional.of(TestData.plan(2L, "Legacy", 1, "900", false)));
        when(planRepository.findById(99L)).thenReturn(Optional.empty());
        when(memberService.findMemberEntity(404L)).thenThrow(new MemberNotFoundException(404L));

        assertThrows(com.gymms.exception.InvalidMembershipException.class, () -> service.recordPayment(
                new PaymentDto.Request(1L, 2L, null, PaymentMethod.CASH, null, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.recordPayment(
                new PaymentDto.Request(1L, 99L, null, PaymentMethod.CASH, null, null)));
        assertThrows(MemberNotFoundException.class, () -> service.recordPayment(request(404L, null, null, null)));
    }

    // ------------------------------------------------------------------ updateStatus

    @Test
    @DisplayName("settling a pending payment marks it PAID and updates the member")
    void settlePendingPayment() {
        Member m = TestData.member(1L, "Arjun Patel", standard, TODAY.minusDays(5), TODAY.plusDays(85));
        m.setPaymentStatus(PaymentStatus.PENDING);
        Payment pending = new Payment();
        pending.setId(5L);
        pending.setMember(m);
        pending.setPlan(standard);
        pending.setAmount(bd("3000.00"));
        pending.setFinalAmount(bd("3000.00"));
        pending.setDiscountPercent(BigDecimal.ZERO);
        pending.setMethod(PaymentMethod.CASH);
        pending.setStatus(PaymentStatus.PENDING);
        pending.setPaymentDate(TODAY);
        pending.setTransactionReference("REF-5");
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(pending));

        PaymentDto.Response r = service.updateStatus(5L, PaymentStatus.PAID);

        assertEquals(PaymentStatus.PAID, r.status());
        assertEquals(PaymentStatus.PAID, m.getPaymentStatus());
    }

    @Test
    @DisplayName("only pending payments can be updated, and only to PAID or FAILED")
    void invalidStatusTransitions() {
        Member m = TestData.member(1L, "Arjun Patel", standard, TODAY.minusDays(5), TODAY.plusDays(85));
        Payment paid = new Payment();
        paid.setId(6L);
        paid.setMember(m);
        paid.setStatus(PaymentStatus.PAID);
        Payment pending = new Payment();
        pending.setId(7L);
        pending.setMember(m);
        pending.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(6L)).thenReturn(Optional.of(paid));
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(pending));
        when(paymentRepository.findById(8L)).thenReturn(Optional.empty());

        assertThrows(InvalidPaymentException.class, () -> service.updateStatus(6L, PaymentStatus.FAILED));
        assertThrows(InvalidPaymentException.class, () -> service.updateStatus(7L, PaymentStatus.PENDING));
        assertThrows(InvalidPaymentException.class, () -> service.updateStatus(7L, null));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(8L, PaymentStatus.PAID));
    }
}

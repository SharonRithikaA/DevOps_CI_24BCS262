package com.gymms.service;

import com.gymms.dto.MemberDto;
import com.gymms.entity.Gender;
import com.gymms.entity.Member;
import com.gymms.entity.MembershipPlan;
import com.gymms.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Shared, deterministic fixtures for the unit tests. "Today" is always 15 March 2026, 10:30:45 UTC. */
final class TestData {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-15T10:30:45Z"), ZoneOffset.UTC);
    static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    private TestData() {
    }

    static MembershipPlan plan(long id, String name, int months, String price, boolean active) {
        MembershipPlan p = new MembershipPlan();
        p.setId(id);
        p.setName(name);
        p.setDurationMonths(months);
        p.setPrice(new BigDecimal(price));
        p.setActive(active);
        return p;
    }

    /** Standard plan: 3 months for Rs 3000 (the example used in the assignment brief). */
    static MembershipPlan standardPlan() {
        return plan(1L, "Standard", 3, "3000", true);
    }

    static Member member(long id, String name, MembershipPlan plan, LocalDate start, LocalDate end) {
        Member m = new Member();
        m.setId(id);
        m.setMemberCode(String.format("GYM-%04d", id));
        m.setFullName(name);
        m.setEmail(name.toLowerCase().replace(" ", ".") + "@example.com");
        m.setPhone("98400000" + String.format("%02d", id));
        m.setDateOfBirth(LocalDate.of(1995, 5, 20));
        m.setGender(Gender.MALE);
        m.setEmergencyContact("Contact - 9000000000");
        m.setJoinDate(start);
        m.setPlan(plan);
        m.setMembershipStartDate(start);
        m.setMembershipEndDate(end);
        m.setPaymentStatus(PaymentStatus.PAID);
        return m;
    }

    static MemberDto.Request request(String email, String phone, LocalDate dob, Long planId,
                                     LocalDate start, LocalDate end, Long trainerId) {
        return new MemberDto.Request("Test Member", email, phone, dob, Gender.FEMALE, "1 Test Street",
                "Parent - 9111111111", planId, start, end, trainerId);
    }

    static MemberDto.Request validRequest(Long planId) {
        return request("test.member@example.com", "9840012345", LocalDate.of(1998, 1, 10), planId, null, null, null);
    }
}

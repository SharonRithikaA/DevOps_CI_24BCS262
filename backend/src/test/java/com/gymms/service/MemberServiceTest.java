package com.gymms.service;

import com.gymms.dto.MemberDto;
import com.gymms.entity.Member;
import com.gymms.entity.MembershipPlan;
import com.gymms.entity.MembershipStatus;
import com.gymms.entity.PaymentStatus;
import com.gymms.exception.DuplicateResourceException;
import com.gymms.exception.InvalidMembershipException;
import com.gymms.exception.MemberNotFoundException;
import com.gymms.exception.ResourceNotFoundException;
import com.gymms.repository.MemberRepository;
import com.gymms.repository.MembershipPlanRepository;
import com.gymms.repository.TrainerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.gymms.service.TestData.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MemberService")
class MemberServiceTest {

    private MemberRepository memberRepository;
    private MembershipPlanRepository planRepository;
    private TrainerRepository trainerRepository;
    private MemberService service;
    private MembershipPlan standard;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        planRepository = mock(MembershipPlanRepository.class);
        trainerRepository = mock(TrainerRepository.class);
        service = new MemberService(memberRepository, planRepository, trainerRepository,
                new MembershipService(TestData.CLOCK));

        standard = TestData.standardPlan();
        when(planRepository.findById(1L)).thenReturn(Optional.of(standard));
        // behave like the database: assign an id on first save
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> {
            Member m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(7L);
            }
            return m;
        });
    }

    // ------------------------------------------------------------------ register

    @Test
    @DisplayName("registers a valid member: generates id, dates and PENDING payment status")
    void registerValidMember() {
        MemberDto.Response response = service.registerMember(TestData.validRequest(1L));

        assertNotNull(response.id());
        assertEquals("GYM-0007", response.memberCode());
        assertEquals("test.member@example.com", response.email());
        assertEquals(TODAY, response.joinDate());
        assertEquals(TODAY, response.membershipStartDate());
        assertEquals(LocalDate.of(2026, 6, 14), response.membershipEndDate());
        assertEquals(MembershipStatus.ACTIVE, response.membershipStatus());
        assertEquals(PaymentStatus.PENDING, response.paymentStatus());
        assertNull(response.trainerId());
        verify(memberRepository, times(2)).save(any(Member.class));
    }

    @Test
    @DisplayName("rejects a duplicate e-mail and does not save")
    void rejectDuplicateEmail() {
        when(memberRepository.existsByEmailIgnoreCase("test.member@example.com")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> service.registerMember(TestData.validRequest(1L)));
        assertTrue(ex.getMessage().contains("e-mail"));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("rejects a duplicate phone number")
    void rejectDuplicatePhone() {
        when(memberRepository.existsByPhone("9840012345")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.registerMember(TestData.validRequest(1L)));
    }

    @Test
    @DisplayName("rejects an inactive membership plan")
    void rejectInactivePlan() {
        when(planRepository.findById(2L)).thenReturn(Optional.of(TestData.plan(2L, "Legacy", 1, "999", false)));
        assertThrows(InvalidMembershipException.class, () -> service.registerMember(TestData.validRequest(2L)));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("rejects an unknown membership plan")
    void rejectUnknownPlan() {
        when(planRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.registerMember(TestData.validRequest(99L)));
    }

    @Test
    @DisplayName("rejects an end date before the start date")
    void rejectEndBeforeStart() {
        MemberDto.Request bad = TestData.request("a@example.com", "9840011111", LocalDate.of(1990, 1, 1), 1L,
                TODAY, TODAY.minusDays(1), null);
        assertThrows(InvalidMembershipException.class, () -> service.registerMember(bad));
    }

    @Test
    @DisplayName("rejects a date of birth that is today or in the future")
    void rejectFutureDateOfBirth() {
        MemberDto.Request bad = TestData.request("a@example.com", "9840011111", TODAY, 1L, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.registerMember(bad));
    }

    @Test
    @DisplayName("rejects an unknown trainer")
    void rejectUnknownTrainer() {
        when(trainerRepository.findById(5L)).thenReturn(Optional.empty());
        MemberDto.Request bad = TestData.request("a@example.com", "9840011111", LocalDate.of(1990, 1, 1), 1L,
                null, null, 5L);
        assertThrows(ResourceNotFoundException.class, () -> service.registerMember(bad));
    }

    // ------------------------------------------------------------------ find / delete / update

    @Test
    @DisplayName("finds an existing member")
    void findExistingMember() {
        Member m = TestData.member(3L, "Asha Active", standard, TODAY.minusDays(10), TODAY.plusDays(50));
        when(memberRepository.findById(3L)).thenReturn(Optional.of(m));

        MemberDto.Response response = service.getMember(3L);

        assertEquals("Asha Active", response.fullName());
        assertEquals(50, response.remainingDays());
        assertEquals("Standard", response.planName());
        assertNotNull(response.email());
    }

    @Test
    @DisplayName("throws MemberNotFoundException when the member does not exist")
    void memberNotFound() {
        when(memberRepository.findById(42L)).thenReturn(Optional.empty());
        MemberNotFoundException ex = assertThrows(MemberNotFoundException.class, () -> service.getMember(42L));
        assertEquals("Member not found with id 42", ex.getMessage());
    }

    @Test
    @DisplayName("deleting a missing member throws and deletes nothing")
    void deleteMissing() {
        when(memberRepository.findById(42L)).thenReturn(Optional.empty());
        assertThrows(MemberNotFoundException.class, () -> service.deleteMember(42L));
        verify(memberRepository, never()).delete(any(Member.class));
    }

    @Test
    @DisplayName("deletes an existing member")
    void deleteExisting() {
        Member m = TestData.member(3L, "Asha Active", standard, TODAY.minusDays(10), TODAY.plusDays(50));
        when(memberRepository.findById(3L)).thenReturn(Optional.of(m));
        service.deleteMember(3L);
        verify(memberRepository).delete(m);
    }

    @Test
    @DisplayName("updates member details")
    void updateMember() {
        Member m = TestData.member(3L, "Asha Active", standard, TODAY.minusDays(10), TODAY.plusDays(50));
        when(memberRepository.findById(3L)).thenReturn(Optional.of(m));

        MemberDto.Response response = service.updateMember(3L, TestData.request("new.mail@example.com", "9840099999",
                LocalDate.of(1992, 2, 2), 1L, TODAY.minusDays(10), TODAY.plusDays(50), null));

        assertEquals("Test Member", response.fullName());
        assertEquals("new.mail@example.com", response.email());
        assertEquals(TODAY.plusDays(50), response.membershipEndDate());
    }

    @Test
    @DisplayName("update rejects an e-mail that belongs to another member")
    void updateDuplicateEmail() {
        Member m = TestData.member(3L, "Asha Active", standard, TODAY.minusDays(10), TODAY.plusDays(50));
        when(memberRepository.findById(3L)).thenReturn(Optional.of(m));
        when(memberRepository.existsByEmailIgnoreCaseAndIdNot("test.member@example.com", 3L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.updateMember(3L, TestData.validRequest(1L)));
        verify(memberRepository, never()).save(any(Member.class));
    }

    // ------------------------------------------------------------------ search

    private void givenThreeMembers() {
        Member active = TestData.member(1L, "Asha Active", standard, TODAY.minusDays(10), TODAY.plusDays(50));
        Member expired = TestData.member(2L, "Ben Expired", standard, TODAY.minusDays(100), TODAY.minusDays(10));
        Member soon = TestData.member(3L, "Cara Soon", TestData.plan(2L, "Basic", 1, "1200", true),
                TODAY.minusDays(25), TODAY.plusDays(5));
        soon.setPaymentStatus(PaymentStatus.PENDING);
        when(memberRepository.findAll()).thenReturn(List.of(active, expired, soon));
    }

    @Test
    @DisplayName("search: default order is newest (highest id) first")
    void searchDefaultOrder() {
        givenThreeMembers();
        List<MemberDto.Response> result = service.search(null, null, null, null, null, null);
        assertEquals(List.of(3L, 2L, 1L), result.stream().map(MemberDto.Response::id).toList());
    }

    @Test
    @DisplayName("search: ACTIVE includes members expiring soon, EXPIRED only expired ones")
    void searchByStatus() {
        givenThreeMembers();
        assertEquals(2, service.search(null, "ACTIVE", null, null, "name", "asc").size());
        List<MemberDto.Response> expired = service.search(null, "EXPIRED", null, null, null, null);
        assertEquals(1, expired.size());
        assertEquals("Ben Expired", expired.get(0).fullName());
        assertEquals(1, service.search(null, "EXPIRING_SOON", null, null, null, null).size());
    }

    @Test
    @DisplayName("search: by name, member id, phone and e-mail (case-insensitive)")
    void searchByText() {
        givenThreeMembers();
        assertEquals(1, service.search("BEN", null, null, null, null, null).size());
        assertEquals(1, service.search("gym-0003", null, null, null, null, null).size());
        assertEquals(1, service.search("9840000001", null, null, null, null, null).size());
        assertEquals(1, service.search("cara.soon@", null, null, null, null, null).size());
        assertTrue(service.search("nobody", null, null, null, null, null).isEmpty());
    }

    @Test
    @DisplayName("search: by plan and payment status, sorted by name ascending")
    void searchByPlanPaymentAndSort() {
        givenThreeMembers();
        assertEquals(2, service.search(null, null, 1L, null, "name", "asc").size());
        assertEquals(1, service.search(null, null, null, PaymentStatus.PENDING, null, null).size());
        List<MemberDto.Response> sorted = service.search(null, null, null, null, "name", "asc");
        assertEquals("Asha Active", sorted.get(0).fullName());
        assertEquals("Cara Soon", sorted.get(2).fullName());
    }
}
package com.gymms.service;

import com.gymms.dto.AttendanceDto;
import com.gymms.entity.Attendance;
import com.gymms.entity.Member;
import com.gymms.entity.MembershipPlan;
import com.gymms.exception.DuplicateAttendanceException;
import com.gymms.exception.InvalidMembershipException;
import com.gymms.exception.MemberNotFoundException;
import com.gymms.repository.AttendanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static com.gymms.service.TestData.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AttendanceService")
class AttendanceServiceTest {

    private AttendanceRepository attendanceRepository;
    private MemberService memberService;
    private AttendanceService service;
    private MembershipPlan standard;

    @BeforeEach
    void setUp() {
        attendanceRepository = mock(AttendanceRepository.class);
        memberService = mock(MemberService.class);
        service = new AttendanceService(attendanceRepository, memberService,
                new MembershipService(TestData.CLOCK), TestData.CLOCK);
        standard = TestData.standardPlan();
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> {
            Attendance a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });
    }

    private Member activeMember() {
        Member m = TestData.member(1L, "Asha Active", standard, TODAY.minusDays(10), TODAY.plusDays(50));
        when(memberService.findMemberEntity(1L)).thenReturn(m);
        return m;
    }

    @Test
    @DisplayName("marks attendance for an active member with today's date and the current time")
    void markAttendance() {
        activeMember();
        AttendanceDto.Response r = service.markAttendance(1L);

        assertEquals(TODAY, r.attendanceDate());
        assertEquals(LocalTime.of(10, 30, 45), r.checkInTime());
        assertEquals("Asha Active", r.memberName());
        verify(attendanceRepository).save(any(Attendance.class));
    }

    @Test
    @DisplayName("a membership expiring today can still check in")
    void lastDayCanCheckIn() {
        Member m = TestData.member(1L, "Asha Active", standard, TODAY.minusDays(30), TODAY);
        when(memberService.findMemberEntity(1L)).thenReturn(m);
        assertEquals(TODAY, service.markAttendance(1L).attendanceDate());
    }

    @Test
    @DisplayName("prevents duplicate attendance for the same member on the same day")
    void duplicateAttendance() {
        activeMember();
        when(attendanceRepository.existsByMemberIdAndAttendanceDate(1L, TODAY)).thenReturn(true);

        DuplicateAttendanceException ex = assertThrows(DuplicateAttendanceException.class, () -> service.markAttendance(1L));
        assertTrue(ex.getMessage().contains("already marked present"));
        verify(attendanceRepository, never()).save(any(Attendance.class));
    }

    @Test
    @DisplayName("rejects attendance for an expired membership")
    void expiredMembership() {
        Member m = TestData.member(1L, "Ben Expired", standard, TODAY.minusDays(100), TODAY.minusDays(1));
        when(memberService.findMemberEntity(1L)).thenReturn(m);
        assertThrows(InvalidMembershipException.class, () -> service.markAttendance(1L));
        verify(attendanceRepository, never()).save(any(Attendance.class));
    }

    @Test
    @DisplayName("rejects attendance before the membership has started")
    void upcomingMembership() {
        Member m = TestData.member(1L, "Dev Later", standard, TODAY.plusDays(3), TODAY.plusDays(93));
        when(memberService.findMemberEntity(1L)).thenReturn(m);
        assertThrows(InvalidMembershipException.class, () -> service.markAttendance(1L));
    }

    @Test
    @DisplayName("propagates MemberNotFoundException for an unknown member")
    void unknownMember() {
        when(memberService.findMemberEntity(9L)).thenThrow(new MemberNotFoundException(9L));
        assertThrows(MemberNotFoundException.class, () -> service.markAttendance(9L));
    }

    @Test
    @DisplayName("today's attendance and text search")
    void todayAndSearch() {
        Member asha = TestData.member(1L, "Asha Active", standard, TODAY.minusDays(10), TODAY.plusDays(50));
        Member ben = TestData.member(2L, "Ben Strong", standard, TODAY.minusDays(10), TODAY.plusDays(50));
        Attendance a1 = attendance(1L, asha, TODAY, LocalTime.of(7, 0));
        Attendance a2 = attendance(2L, ben, TODAY, LocalTime.of(8, 0));
        when(attendanceRepository.findByAttendanceDateOrderByCheckInTimeDesc(TODAY)).thenReturn(List.of(a2, a1));

        assertEquals(2, service.getToday().size());
        List<AttendanceDto.Response> filtered = service.search(TODAY, "ben", null);
        assertEquals(1, filtered.size());
        assertEquals("Ben Strong", filtered.get(0).memberName());
        assertEquals(1, service.search(TODAY, "GYM-0001", null).size());
    }

    @Test
    @DisplayName("member history is returned newest first and requires an existing member")
    void history() {
        Member asha = TestData.member(1L, "Asha Active", standard, TODAY.minusDays(10), TODAY.plusDays(50));
        when(memberService.findMemberEntity(1L)).thenReturn(asha);
        when(attendanceRepository.findByMemberIdOrderByAttendanceDateDesc(1L)).thenReturn(List.of(
                attendance(2L, asha, TODAY, LocalTime.of(7, 0)),
                attendance(1L, asha, TODAY.minusDays(1), LocalTime.of(7, 5))));

        List<AttendanceDto.Response> history = service.getHistory(1L);
        assertEquals(2, history.size());
        assertEquals(TODAY, history.get(0).attendanceDate());

        when(memberService.findMemberEntity(5L)).thenThrow(new MemberNotFoundException(5L));
        assertThrows(MemberNotFoundException.class, () -> service.getHistory(5L));
    }

    private Attendance attendance(long id, Member member, java.time.LocalDate date, LocalTime time) {
        Attendance a = new Attendance();
        a.setId(id);
        a.setMember(member);
        a.setAttendanceDate(date);
        a.setCheckInTime(time);
        return a;
    }
}

package com.gymms.service;

import com.gymms.dto.AttendanceDto;
import com.gymms.entity.Attendance;
import com.gymms.entity.Member;
import com.gymms.entity.MembershipStatus;
import com.gymms.exception.DuplicateAttendanceException;
import com.gymms.exception.InvalidMembershipException;
import com.gymms.repository.AttendanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Marks and queries gym check-ins. One check-in per member per day; only valid memberships may check in. */
@Service
@Transactional
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final MemberService memberService;
    private final MembershipService membershipService;
    private final Clock clock;

    public AttendanceService(AttendanceRepository attendanceRepository, MemberService memberService,
                             MembershipService membershipService, Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.memberService = memberService;
        this.membershipService = membershipService;
        this.clock = clock;
    }

    public AttendanceDto.Response markAttendance(Long memberId) {
        Member member = memberService.findMemberEntity(memberId);

        MembershipStatus status = membershipService.getStatus(member.getMembershipStartDate(), member.getMembershipEndDate());
        if (status == MembershipStatus.EXPIRED) {
            throw new InvalidMembershipException("Membership expired on " + member.getMembershipEndDate()
                    + ". Renew it before marking attendance.");
        }
        if (status == MembershipStatus.UPCOMING) {
            throw new InvalidMembershipException("Membership starts on " + member.getMembershipStartDate());
        }

        LocalDate today = membershipService.today();
        if (attendanceRepository.existsByMemberIdAndAttendanceDate(memberId, today)) {
            throw new DuplicateAttendanceException(member.getFullName() + " is already marked present today");
        }

        Attendance attendance = new Attendance();
        attendance.setMember(member);
        attendance.setAttendanceDate(today);
        attendance.setCheckInTime(LocalTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        return toResponse(attendanceRepository.save(attendance));
    }

    /**
     * @param date     optional exact day
     * @param query    optional member name / member id text
     * @param memberId optional member (attendance history)
     */
    @Transactional(readOnly = true)
    public List<AttendanceDto.Response> search(LocalDate date, String query, Long memberId) {
        List<Attendance> base;
        if (memberId != null) {
            base = attendanceRepository.findByMemberIdOrderByAttendanceDateDesc(memberId);
        } else if (date != null) {
            base = attendanceRepository.findByAttendanceDateOrderByCheckInTimeDesc(date);
        } else {
            base = attendanceRepository.findAllByOrderByAttendanceDateDescCheckInTimeDesc();
        }
        String q = query == null ? "" : query.trim().toLowerCase();
        return base.stream()
                .filter(a -> date == null || a.getAttendanceDate().equals(date))
                .filter(a -> q.isEmpty()
                        || a.getMember().getFullName().toLowerCase().contains(q)
                        || (a.getMember().getMemberCode() != null && a.getMember().getMemberCode().toLowerCase().contains(q)))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto.Response> getToday() {
        return search(membershipService.today(), null, null);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto.Response> getHistory(Long memberId) {
        memberService.findMemberEntity(memberId);
        return search(null, null, memberId);
    }

    private AttendanceDto.Response toResponse(Attendance a) {
        Member m = a.getMember();
        return new AttendanceDto.Response(a.getId(), m.getId(), m.getMemberCode(), m.getFullName(),
                m.getPlan().getName(), a.getAttendanceDate(), a.getCheckInTime());
    }
}

package com.gymms.repository;

import com.gymms.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    boolean existsByMemberIdAndAttendanceDate(Long memberId, LocalDate date);
    long countByAttendanceDate(LocalDate date);
    List<Attendance> findAllByOrderByAttendanceDateDescCheckInTimeDesc();
    List<Attendance> findByAttendanceDateOrderByCheckInTimeDesc(LocalDate date);
    List<Attendance> findByMemberIdOrderByAttendanceDateDesc(Long memberId);
    List<Attendance> findByAttendanceDateBetween(LocalDate from, LocalDate to);
}

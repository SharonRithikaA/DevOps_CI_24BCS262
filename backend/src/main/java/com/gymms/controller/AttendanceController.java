package com.gymms.controller;

import com.gymms.dto.AttendanceDto;
import com.gymms.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceDto.Response mark(@Valid @RequestBody AttendanceDto.Request request) {
        return attendanceService.markAttendance(request.memberId());
    }

    @GetMapping("/today")
    public List<AttendanceDto.Response> today() {
        return attendanceService.getToday();
    }

    @GetMapping
    public List<AttendanceDto.Response> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long memberId) {
        return attendanceService.search(date, q, memberId);
    }
}

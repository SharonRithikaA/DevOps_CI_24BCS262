package com.gymms.controller;

import com.gymms.dto.AttendanceDto;
import com.gymms.dto.MemberDto;
import com.gymms.dto.PaymentDto;
import com.gymms.entity.PaymentStatus;
import com.gymms.service.AttendanceService;
import com.gymms.service.MemberService;
import com.gymms.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;
    private final PaymentService paymentService;
    private final AttendanceService attendanceService;

    public MemberController(MemberService memberService, PaymentService paymentService,
                            AttendanceService attendanceService) {
        this.memberService = memberService;
        this.paymentService = paymentService;
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public List<MemberDto.Response> search(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) Long planId,
                                           @RequestParam(required = false) PaymentStatus paymentStatus,
                                           @RequestParam(required = false) String sortBy,
                                           @RequestParam(required = false) String direction) {
        return memberService.search(q, status, planId, paymentStatus, sortBy, direction);
    }

    @GetMapping("/{id}")
    public MemberDto.Response get(@PathVariable Long id) {
        return memberService.getMember(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDto.Response create(@Valid @RequestBody MemberDto.Request request) {
        return memberService.registerMember(request);
    }

    @PutMapping("/{id}")
    public MemberDto.Response update(@PathVariable Long id, @Valid @RequestBody MemberDto.Request request) {
        return memberService.updateMember(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberService.deleteMember(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/payments")
    public List<PaymentDto.Response> payments(@PathVariable Long id) {
        return paymentService.listForMember(id);
    }

    @GetMapping("/{id}/attendance")
    public List<AttendanceDto.Response> attendance(@PathVariable Long id) {
        return attendanceService.getHistory(id);
    }
}

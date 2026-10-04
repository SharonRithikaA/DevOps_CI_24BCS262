package com.gymms.controller;

import com.gymms.dto.PaymentDto;
import com.gymms.entity.PaymentMethod;
import com.gymms.entity.PaymentStatus;
import com.gymms.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PaymentDto.Response> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) PaymentStatus status,
                                          @RequestParam(required = false) PaymentMethod method,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return paymentService.list(q, status, method, from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDto.Response record(@Valid @RequestBody PaymentDto.Request request) {
        return paymentService.recordPayment(request);
    }

    @PutMapping("/{id}/status")
    public PaymentDto.Response updateStatus(@PathVariable Long id, @Valid @RequestBody PaymentDto.StatusRequest request) {
        return paymentService.updateStatus(id, request.status());
    }
}

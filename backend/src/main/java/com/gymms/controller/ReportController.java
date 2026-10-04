package com.gymms.controller;

import com.gymms.dto.ReportTable;
import com.gymms.entity.PaymentStatus;
import com.gymms.service.CsvWriter;
import com.gymms.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** type = membership | active | expired | revenue | attendance */
    @GetMapping("/{type}")
    public ReportTable report(@PathVariable String type,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @RequestParam(required = false) Long planId,
                              @RequestParam(required = false) PaymentStatus paymentStatus) {
        return reportService.build(type, from, to, planId, paymentStatus);
    }

    @GetMapping("/{type}/csv")
    public ResponseEntity<byte[]> csv(@PathVariable String type,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) Long planId,
                                      @RequestParam(required = false) PaymentStatus paymentStatus) {
        ReportTable table = reportService.build(type, from, to, planId, paymentStatus);
        // UTF-8 BOM so Excel opens the file with the right encoding
        byte[] body = ("\uFEFF" + CsvWriter.toCsv(table)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(type + "-report.csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}

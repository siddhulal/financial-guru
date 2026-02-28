package com.financialguru.controller;

import com.financialguru.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/transactions/csv")
    public ResponseEntity<byte[]> exportCSV(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String category) {
        byte[] data = exportService.exportTransactionsCSV(from, to, accountId, category);
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=transactions_" + from + "_to_" + to + ".csv")
                .header("Content-Type", "text/csv")
                .body(data);
    }

    @GetMapping("/monthly-pdf")
    public ResponseEntity<byte[]> exportMonthlyPDF(
            @RequestParam int year,
            @RequestParam int month) throws Exception {
        byte[] data = exportService.exportMonthlySummaryPDF(year, month);
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=monthly-summary-" + year + "-" + month + ".pdf")
                .header("Content-Type", "application/pdf")
                .body(data);
    }
}

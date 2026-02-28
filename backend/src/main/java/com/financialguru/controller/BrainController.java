package com.financialguru.controller;

import com.financialguru.dto.response.BrainReportResponse;
import com.financialguru.service.FinancialBrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/brain")
@RequiredArgsConstructor
public class BrainController {

    private final FinancialBrainService financialBrainService;

    @GetMapping
    public ResponseEntity<BrainReportResponse> getBrainReport() {
        return ResponseEntity.ok(financialBrainService.generateReport());
    }
}

package com.financialguru.controller;

import com.financialguru.dto.response.AnalysisResponse;
import com.financialguru.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Tag(name = "Analysis", description = "AI analysis of statements")
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping("/run/{statementId}")
    @Operation(summary = "Trigger full AI analysis for a statement")
    public ResponseEntity<Map<String, String>> runAnalysis(@PathVariable UUID statementId) {
        analysisService.runFullAnalysis(statementId);
        return ResponseEntity.ok(Map.of(
            "status", "STARTED",
            "message", "AI analysis started. Results will be available shortly."
        ));
    }

    @GetMapping("/{statementId}")
    @Operation(summary = "Get AI analysis results for a statement")
    public ResponseEntity<List<AnalysisResponse>> getAnalysis(@PathVariable UUID statementId) {
        return ResponseEntity.ok(analysisService.getAnalysisResults(statementId));
    }
}

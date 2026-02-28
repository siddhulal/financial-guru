package com.financialguru.controller;

import com.financialguru.dto.response.AnnualReviewResponse;
import com.financialguru.dto.response.CashFlowResponse;
import com.financialguru.dto.response.DebtPayoffResponse;
import com.financialguru.dto.response.HealthScoreResponse;
import com.financialguru.dto.response.WhatIfDataPoint;
import com.financialguru.model.Insight;
import com.financialguru.repository.InsightRepository;
import com.financialguru.service.AnnualReviewService;
import com.financialguru.service.CashFlowService;
import com.financialguru.service.DebtPayoffService;
import com.financialguru.service.FinancialHealthService;
import com.financialguru.service.InsightEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InsightController {

    private final DebtPayoffService debtPayoffService;
    private final FinancialHealthService financialHealthService;
    private final InsightEngineService insightEngineService;
    private final CashFlowService cashFlowService;
    private final AnnualReviewService annualReviewService;
    private final InsightRepository insightRepository;

    @GetMapping("/debt-payoff")
    public DebtPayoffResponse getDebtPayoff(
            @RequestParam(defaultValue = "0") BigDecimal extra) {
        return debtPayoffService.calculatePayoff(extra);
    }

    @GetMapping("/debt-payoff/what-if")
    public List<WhatIfDataPoint> getWhatIf() {
        return debtPayoffService.calculateWhatIfRange();
    }

    @GetMapping("/health-score")
    public HealthScoreResponse getHealthScore() {
        return financialHealthService.computeScore();
    }

    @GetMapping("/cash-flow")
    public CashFlowResponse getCashFlow(
            @RequestParam int year,
            @RequestParam int month) {
        return cashFlowService.getCashFlowCalendar(year, month);
    }

    @GetMapping("/annual-review")
    public AnnualReviewResponse getAnnualReview(@RequestParam int year) {
        return annualReviewService.getAnnualReview(year);
    }

    @GetMapping("")
    public List<Insight> getInsights() {
        return insightRepository.findByIsDismissedFalseOrderByGeneratedAtDesc();
    }

    @PostMapping("/run")
    public Map<String, Integer> runInsights() {
        List<Insight> generated = insightEngineService.runAll();
        return Map.of("count", generated.size());
    }

    @PutMapping("/{id}/dismiss")
    @Transactional
    public Insight dismissInsight(@PathVariable UUID id) {
        Insight insight = insightRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Insight not found: " + id));
        insight.setIsDismissed(true);
        return insightRepository.save(insight);
    }
}

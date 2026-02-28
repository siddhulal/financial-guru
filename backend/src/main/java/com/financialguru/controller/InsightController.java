package com.financialguru.controller;

import com.financialguru.dto.response.AnnualReviewResponse;
import com.financialguru.dto.response.CashFlowResponse;
import com.financialguru.dto.response.CreditScoreResponse;
import com.financialguru.dto.response.DebtPayoffResponse;
import com.financialguru.dto.response.DuplicateTransactionGroup;
import com.financialguru.dto.response.FireCalculatorResponse;
import com.financialguru.dto.response.HealthScoreResponse;
import com.financialguru.dto.response.MerchantTrendResponse;
import com.financialguru.dto.response.SpendingHeatmapResponse;
import com.financialguru.dto.response.WhatIfDataPoint;
import com.financialguru.model.Insight;
import com.financialguru.repository.InsightRepository;
import com.financialguru.service.AnnualReviewService;
import com.financialguru.service.CashFlowService;
import com.financialguru.service.CreditScoreService;
import com.financialguru.service.DebtPayoffService;
import com.financialguru.service.FireCalculatorService;
import com.financialguru.service.FinancialHealthService;
import com.financialguru.service.InsightEngineService;
import com.financialguru.service.SpendingHeatmapService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final SpendingHeatmapService spendingHeatmapService;
    private final FireCalculatorService fireCalculatorService;
    private final CreditScoreService creditScoreService;

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

    @GetMapping("/spending-heatmap")
    public SpendingHeatmapResponse getHeatmap(@RequestParam(required = false) Integer year) {
        return spendingHeatmapService.getHeatmap(year != null ? year : LocalDate.now().getYear());
    }

    @GetMapping("/merchant-trend")
    public MerchantTrendResponse getMerchantTrend(@RequestParam String merchant) {
        return spendingHeatmapService.getMerchantTrend(merchant);
    }

    @GetMapping("/credit-score")
    public CreditScoreResponse getCreditScore() {
        return creditScoreService.getOptimization();
    }

    @GetMapping("/fire-calculator")
    public FireCalculatorResponse getFireCalculator(
            @RequestParam(required = false) BigDecimal age,
            @RequestParam(required = false) BigDecimal targetRetirementAge,
            @RequestParam(required = false) BigDecimal currentInvestments,
            @RequestParam(required = false) BigDecimal monthlyExpenses) {
        return fireCalculatorService.calculate(age, targetRetirementAge, currentInvestments, monthlyExpenses);
    }

    @GetMapping("/duplicates")
    public List<DuplicateTransactionGroup> getDuplicates() {
        return spendingHeatmapService.findDuplicates();
    }
}

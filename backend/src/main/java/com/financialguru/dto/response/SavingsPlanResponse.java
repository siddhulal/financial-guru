package com.financialguru.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SavingsPlanResponse {

    // Current financial state
    private BigDecimal monthlyIncome;
    private BigDecimal currentMonthlySpend;
    private BigDecimal currentMonthlySavings;
    private BigDecimal currentSavingsRatePct;

    // Goal
    private BigDecimal targetAdditionalSavings;
    private BigDecimal targetMonthlySavings;
    private BigDecimal targetSavingsRatePct;

    // Plan coverage
    private BigDecimal totalRecommendedSavings;
    private BigDecimal coveragePct;           // how much of the gap we can close
    private boolean goalAchievable;

    // AI narrative (Ollama)
    private String aiNarrative;
    private boolean aiAvailable;

    // Ranked recommendations
    private List<CategoryRecommendation> recommendations;

    // Full spending breakdown for context
    private List<SpendingCategory> spendingBreakdown;

    @Data
    @Builder
    public static class CategoryRecommendation {
        private String category;
        private BigDecimal currentMonthlySpend;
        private BigDecimal targetMonthlySpend;
        private BigDecimal monthlySavings;       // the cut amount
        private BigDecimal benchmarkAmount;      // what advisor recommends max
        private double benchmarkPct;             // as % of income
        private String difficulty;               // EASY / MEDIUM / HARD
        private int easeScore;                   // 1-5 (5=easiest)
        private List<TopMerchant> topMerchants;
        private List<String> specificActions;
        private String reasoning;               // one-line explanation
    }

    @Data
    @Builder
    public static class TopMerchant {
        private String name;
        private BigDecimal monthlyAmount;
        private int transactionCount;
    }

    @Data
    @Builder
    public static class SpendingCategory {
        private String category;
        private BigDecimal monthlyAvg;
        private double pctOfIncome;
        private double benchmarkPct;
        private String status;                  // OVER / OK / GOOD
    }
}

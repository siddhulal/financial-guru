package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnualReviewResponse {

    private int year;
    private BigDecimal totalSpending;
    private BigDecimal estimatedIncome;
    private BigDecimal savingsRate;
    private BigDecimal interestPaid;
    private BigDecimal feesPaid;
    private BigDecimal subscriptionAnnualCost;
    private BigDecimal netWorthChange;
    private List<Map<String, Object>> categoryBreakdown;
    private List<String> aiRecommendations;
}

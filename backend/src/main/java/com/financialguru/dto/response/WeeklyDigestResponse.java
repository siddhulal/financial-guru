package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyDigestResponse {
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private BigDecimal totalSpend;
    private BigDecimal priorWeekSpend;
    private BigDecimal spendingChangePercent;
    private List<Map<String, Object>> topTransactions;
    private List<BudgetStatusResponse> budgetStatuses;
    private List<Map<String, Object>> upcomingPayments;
    private List<Map<String, Object>> categoryBreakdown;
    private int unreadInsightCount;
}

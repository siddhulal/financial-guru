package com.financialguru.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardResponse {
    // Balances
    private BigDecimal totalCreditCardBalance;
    private BigDecimal totalCreditLimit;
    private BigDecimal totalAvailableCredit;
    private BigDecimal overallUtilizationPercent;
    private BigDecimal totalCheckingBalance;
    private BigDecimal totalSavingsBalance;

    // Alerts
    private long unreadAlertCount;
    private List<AlertResponse> recentAlerts;

    // Spending
    private BigDecimal currentMonthSpend;
    private BigDecimal lastMonthSpend;
    private BigDecimal spendingChangePercent;
    private List<Map<String, Object>> monthlySpendingTrend;  // [{month, amount}]
    private List<Map<String, Object>> categoryBreakdown;     // [{category, amount, percent}]
    private List<Map<String, Object>> topMerchants;          // [{merchant, amount, count}]

    // Accounts
    private List<AccountResponse> accounts;

    // Upcoming
    private List<Map<String, Object>> upcomingPayments;      // [{account, dueDay, amount}]
    private List<Map<String, Object>> expiringPromoAprs;     // [{account, endDate, daysLeft}]

    // Subscriptions
    private BigDecimal monthlySubscriptionCost;
    private int activeSubscriptionCount;
    private int duplicateSubscriptionCount;
}

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

    // Wealth Advisor KPIs
    private BigDecimal estimatedMonthlyIncome;
    private BigDecimal monthlySavingsRate;        // (income - spend) / income * 100
    private BigDecimal avgSavingsRate6Month;       // avg over last 6 months
    private Integer yearsToRetirementAtCurrentRate; // simple projection, null if no data
    private BigDecimal freedomMonths;             // liquid assets / monthly expenses
    private BigDecimal freedomMonthsTrend;        // this month net cash flow (positive = growing)
    private BigDecimal materialSpendThisMonth;    // SHOPPING + CLOTHING + ELECTRONICS this month
    private BigDecimal materialSpendLastMonth;    // same for last month
    private BigDecimal thingsSpend;               // material goods this month
    private BigDecimal experiencesSpend;          // dining + travel + entertainment this month
    private BigDecimal necessitiesSpend;          // housing + utilities + transport this month
    private List<Map<String, Object>> paycheckBreakdown; // [{label, amount, pctOfIncome, bucket}]
}

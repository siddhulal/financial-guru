package com.financialguru.service;

import com.financialguru.dto.response.HealthScoreResponse;
import com.financialguru.dto.response.HealthScoreResponse.ScorePillar;
import com.financialguru.model.Account;
import com.financialguru.model.Alert;
import com.financialguru.model.FinancialProfile;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.AlertRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialHealthService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal BD100 = BigDecimal.valueOf(100);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final FinancialProfileService financialProfileService;

    public HealthScoreResponse computeScore() {
        List<Account> accounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        FinancialProfile profile = financialProfileService.getOrCreateProfile();
        LocalDate today = LocalDate.now();

        List<ScorePillar> pillars = new ArrayList<>();
        int total = 0;

        // 1. Utilization (0-25)
        BigDecimal totalBalance = ZERO;
        BigDecimal totalLimit = ZERO;
        for (Account a : accounts) {
            if (a.getType() == Account.AccountType.CREDIT_CARD && a.getCurrentBalance() != null) {
                totalBalance = totalBalance.add(a.getCurrentBalance());
                if (a.getCreditLimit() != null) totalLimit = totalLimit.add(a.getCreditLimit());
            }
        }
        BigDecimal utilPct = totalLimit.compareTo(ZERO) > 0
            ? totalBalance.divide(totalLimit, 4, RoundingMode.HALF_UP).multiply(BD100)
            : BigDecimal.valueOf(50);
        int utilScore = utilPct.compareTo(new BigDecimal("10")) <= 0 ? 25
            : utilPct.compareTo(new BigDecimal("30")) <= 0 ? 18
            : utilPct.compareTo(new BigDecimal("70")) <= 0 ? 10 : 3;
        pillars.add(new ScorePillar("Credit Utilization", utilScore, 25,
            String.format("%.1f%% utilization. Keep below 30%% for good score.", utilPct)));
        total += utilScore;

        // 2. Emergency Fund (0-20)
        BigDecimal savingsBalance = accounts.stream()
            .filter(a -> a.getType() == Account.AccountType.SAVINGS && a.getCurrentBalance() != null)
            .map(Account::getCurrentBalance)
            .reduce(ZERO, BigDecimal::add);
        LocalDate sixMonthsAgo = today.minusMonths(6);
        BigDecimal totalSpend6M = transactionRepository.sumAllSpending(sixMonthsAgo, today);
        BigDecimal avgMonthlySpend = totalSpend6M != null && totalSpend6M.compareTo(ZERO) > 0
            ? totalSpend6M.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(3000);
        BigDecimal efMonths = avgMonthlySpend.compareTo(ZERO) > 0
            ? savingsBalance.divide(avgMonthlySpend, 2, RoundingMode.HALF_UP) : ZERO;
        int efScore = efMonths.compareTo(BigDecimal.valueOf(6)) >= 0 ? 20
            : efMonths.compareTo(BigDecimal.valueOf(3)) >= 0 ? 14
            : efMonths.compareTo(BigDecimal.ONE) >= 0 ? 8 : 2;
        pillars.add(new ScorePillar("Emergency Fund", efScore, 20,
            String.format("%.1f months covered (target: %d months).", efMonths,
                profile.getEmergencyFundTargetMonths() != null ? profile.getEmergencyFundTargetMonths() : 6)));
        total += efScore;

        // 3. Savings Rate (0-20)
        int srScore = 10;
        BigDecimal savingsRate = ZERO;
        if (profile.getMonthlyIncome() != null && profile.getMonthlyIncome().compareTo(ZERO) > 0) {
            LocalDate startOfMonth = today.withDayOfMonth(1);
            BigDecimal thisMonthSpend = transactionRepository.sumAllSpending(startOfMonth, today);
            if (thisMonthSpend == null) thisMonthSpend = ZERO;
            savingsRate = profile.getMonthlyIncome().subtract(thisMonthSpend)
                .divide(profile.getMonthlyIncome(), 4, RoundingMode.HALF_UP)
                .multiply(BD100);
            srScore = savingsRate.compareTo(new BigDecimal("20")) >= 0 ? 20
                : savingsRate.compareTo(new BigDecimal("10")) >= 0 ? 15
                : savingsRate.compareTo(new BigDecimal("5")) >= 0 ? 8
                : savingsRate.compareTo(ZERO) > 0 ? 3 : 0;
        }
        pillars.add(new ScorePillar("Savings Rate", srScore, 20,
            profile.getMonthlyIncome() != null
                ? String.format("%.1f%% savings rate this month.", savingsRate)
                : "Set your income to calculate savings rate."));
        total += srScore;

        // 4. Debt Trend (0-15)
        // Compare last month's spending to the 3-month average (months 2-4 ago)
        LocalDate startOld = today.minusMonths(4).withDayOfMonth(1);
        LocalDate endOld   = today.minusMonths(1).withDayOfMonth(1).minusDays(1);
        BigDecimal spend3M = transactionRepository.sumAllSpending(startOld, endOld);
        if (spend3M == null) spend3M = ZERO;
        BigDecimal avgMonthly3M = spend3M.compareTo(ZERO) > 0
            ? spend3M.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP) : ZERO;
        BigDecimal spendRecent = transactionRepository.sumAllSpending(
            today.minusMonths(1).withDayOfMonth(1), today);
        if (spendRecent == null) spendRecent = ZERO;
        int debtScore;
        String debtExplanation;
        if (avgMonthly3M.compareTo(ZERO) == 0) {
            debtScore = 8;
            debtExplanation = "Not enough history to assess spending trend.";
        } else if (spendRecent.compareTo(avgMonthly3M) < 0) {
            debtScore = 15;
            debtExplanation = "Spending trending down vs 3-month average — good debt management.";
        } else if (spendRecent.compareTo(avgMonthly3M.multiply(new BigDecimal("1.10"))) <= 0) {
            debtScore = 8;
            debtExplanation = "Spending stable vs 3-month average — maintain your payment habits.";
        } else {
            debtScore = 2;
            debtExplanation = "Spending up >10% vs 3-month average — watch your credit card balances.";
        }
        pillars.add(new ScorePillar("Debt Trend", debtScore, 15, debtExplanation));
        total += debtScore;

        // 5. Spending Discipline (0-10)
        LocalDate startOfThisMonth = today.withDayOfMonth(1);
        LocalDate startOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate endOfLastMonth = startOfThisMonth.minusDays(1);
        BigDecimal thisMonth = transactionRepository.sumAllSpending(startOfThisMonth, today);
        BigDecimal lastMonth = transactionRepository.sumAllSpending(startOfLastMonth, endOfLastMonth);
        if (thisMonth == null) thisMonth = ZERO;
        if (lastMonth == null) lastMonth = ZERO;
        int discScore = 10;
        String discExpl = "Good spending discipline.";
        if (lastMonth.compareTo(ZERO) > 0) {
            BigDecimal pctChange = thisMonth.subtract(lastMonth)
                .divide(lastMonth, 4, RoundingMode.HALF_UP)
                .multiply(BD100);
            discScore = pctChange.abs().compareTo(new BigDecimal("10")) <= 0 ? 10
                : pctChange.abs().compareTo(new BigDecimal("25")) <= 0 ? 6 : 3;
            discExpl = String.format("Spending %.1f%% vs last month.", pctChange);
        }
        pillars.add(new ScorePillar("Spending Discipline", discScore, 10, discExpl));
        total += discScore;

        // 6. Payment History (0-10)
        List<Alert> dueDateAlerts = alertRepository.findByTypeAndIsResolvedFalseOrderByCreatedAtDesc(
            Alert.AlertType.DUE_DATE);
        long highAlerts = dueDateAlerts.stream()
            .filter(a -> a.getSeverity() == Alert.AlertSeverity.HIGH)
            .count();
        int phScore = highAlerts == 0 ? 10 : highAlerts <= 2 ? 5 : 0;
        pillars.add(new ScorePillar("Payment History", phScore, 10,
            highAlerts == 0 ? "No overdue payments detected." : highAlerts + " overdue payment alerts."));
        total += phScore;

        String grade = total >= 85 ? "A" : total >= 70 ? "B" : total >= 55 ? "C" : total >= 40 ? "D" : "F";

        return HealthScoreResponse.builder()
            .totalScore(total)
            .grade(grade)
            .pillars(pillars)
            .emergencyFundMonths(efMonths)
            .emergencyFundTarget(profile.getEmergencyFundTargetMonths() != null
                ? profile.getEmergencyFundTargetMonths() : 6)
            .utilizationPercent(utilPct)
            .savingsRate(savingsRate)
            .build();
    }
}

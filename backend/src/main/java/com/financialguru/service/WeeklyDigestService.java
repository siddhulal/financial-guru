package com.financialguru.service;

import com.financialguru.dto.response.BudgetStatusResponse;
import com.financialguru.dto.response.WeeklyDigestResponse;
import com.financialguru.model.Account;
import com.financialguru.model.Transaction;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.InsightRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyDigestService {

    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;
    private final InsightRepository insightRepository;
    private final AccountRepository accountRepository;

    public WeeklyDigestResponse getDigest() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        LocalDate priorWeekStart = weekStart.minusDays(7);
        LocalDate priorWeekEnd = weekStart.minusDays(1);

        BigDecimal thisWeek = transactionRepository.sumAllSpending(weekStart, today);
        BigDecimal priorWeek = transactionRepository.sumAllSpending(priorWeekStart, priorWeekEnd);
        if (thisWeek == null) thisWeek = BigDecimal.ZERO;
        if (priorWeek == null) priorWeek = BigDecimal.ZERO;

        BigDecimal changePercent = priorWeek.compareTo(BigDecimal.ZERO) > 0
                ? thisWeek.subtract(priorWeek)
                        .divide(priorWeek, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Top 5 transactions this week by amount
        List<Transaction> weekTxns = transactionRepository.findRecentUnflaggedDebits(weekStart);
        weekTxns.sort(Comparator.comparing(Transaction::getAmount).reversed());
        List<Map<String, Object>> topTxns = weekTxns.stream().limit(5).map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("merchant", t.getMerchantName());
            m.put("amount", t.getAmount());
            m.put("date", t.getTransactionDate());
            m.put("category", t.getCategory());
            return m;
        }).collect(Collectors.toList());

        // Budget statuses
        List<BudgetStatusResponse> budgets = budgetService.getAllBudgetsWithStatus();

        // Category breakdown for this week
        List<Object[]> cats = transactionRepository.findAllCategoryTotals(weekStart, today);
        List<Map<String, Object>> catBreakdown = cats.stream().limit(5).map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", row[0]);
            m.put("amount", row[1]);
            return m;
        }).collect(Collectors.toList());

        // Upcoming payments (next 7 days)
        List<Account> accounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        List<Map<String, Object>> upcoming = new ArrayList<>();
        for (Account a : accounts) {
            if (a.getType() != Account.AccountType.CREDIT_CARD || a.getPaymentDueDay() == null) continue;
            LocalDate due = today.withDayOfMonth(
                    Math.min(a.getPaymentDueDay(), today.lengthOfMonth()));
            if (due.isBefore(today)) due = due.plusMonths(1);
            if (!due.isAfter(today.plusDays(7))) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("account", a.getName());
                m.put("dueDate", due);
                m.put("balance", a.getCurrentBalance());
                upcoming.add(m);
            }
        }

        long unreadInsights = insightRepository.findByIsDismissedFalseOrderByGeneratedAtDesc().size();

        return WeeklyDigestResponse.builder()
                .weekStart(weekStart)
                .weekEnd(today)
                .totalSpend(thisWeek)
                .priorWeekSpend(priorWeek)
                .spendingChangePercent(changePercent)
                .topTransactions(topTxns)
                .budgetStatuses(budgets)
                .upcomingPayments(upcoming)
                .categoryBreakdown(catBreakdown)
                .unreadInsightCount((int) unreadInsights)
                .build();
    }
}

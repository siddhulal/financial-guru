package com.financialguru.service;

import com.financialguru.dto.response.AnnualReviewResponse;
import com.financialguru.model.FinancialProfile;
import com.financialguru.model.NetWorthSnapshot;
import com.financialguru.model.Subscription;
import com.financialguru.model.Transaction;
import com.financialguru.repository.NetWorthSnapshotRepository;
import com.financialguru.repository.SubscriptionRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnualReviewService {

    private final TransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final NetWorthSnapshotRepository netWorthSnapshotRepository;
    private final FinancialProfileService financialProfileService;
    private final OllamaService ollamaService;

    public AnnualReviewResponse getAnnualReview(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        BigDecimal totalSpending = transactionRepository.sumAllSpending(start, end);
        if (totalSpending == null) totalSpending = BigDecimal.ZERO;

        BigDecimal interestPaid = BigDecimal.ZERO;
        BigDecimal feesPaid = BigDecimal.ZERO;
        List<Transaction> feeTransactions = transactionRepository.findAllFeeTransactions(start);
        for (Transaction t : feeTransactions) {
            if (t.getAmount() != null) feesPaid = feesPaid.add(t.getAmount());
        }

        List<Subscription> subs = subscriptionRepository.findByIsActiveTrueOrderByAnnualCostDesc();
        BigDecimal subAnnual = subs.stream()
            .map(s -> s.getAnnualCost() != null ? s.getAnnualCost() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        FinancialProfile profile = financialProfileService.getOrCreateProfile();
        BigDecimal income = profile.getMonthlyIncome() != null
            ? profile.getMonthlyIncome().multiply(BigDecimal.valueOf(12))
            : BigDecimal.ZERO;

        BigDecimal savingsRate = income.compareTo(BigDecimal.ZERO) > 0
            ? income.subtract(totalSpending)
                .divide(income, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        List<NetWorthSnapshot> history = netWorthSnapshotRepository.findSince(start);
        BigDecimal netWorthChange = BigDecimal.ZERO;
        if (history.size() >= 2) {
            netWorthChange = history.get(history.size() - 1).getNetWorth()
                .subtract(history.get(0).getNetWorth());
        }

        List<Object[]> cats = transactionRepository.findAllCategoryTotals(start, end);
        List<Map<String, Object>> catBreakdown = new ArrayList<>();
        for (Object[] row : cats) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", row[0]);
            m.put("amount", row[1]);
            catBreakdown.add(m);
        }

        List<String> recommendations = List.of(
            "Review your top spending categories and set budgets.",
            "Consider automating savings to reach your emergency fund goal.",
            "Negotiate your recurring bills for better rates."
        );

        try {
            BigDecimal finalTotalSpending = totalSpending;
            String catSummary = catBreakdown.stream()
                .limit(5)
                .map(m -> m.get("category") + ": $" + m.get("amount"))
                .collect(Collectors.joining(", "));
            String prompt = String.format("""
                As a financial advisor, provide 3 specific, actionable recommendations based on these annual figures:
                - Total spending: $%.2f
                - Annual subscription cost: $%.2f
                - Savings rate: %.1f%%
                Top categories: %s

                Format: Return exactly 3 recommendations, one per line, starting with a number (1. 2. 3.). Be specific and actionable.
                """, finalTotalSpending, subAnnual, savingsRate, catSummary);

            String aiResponse = ollamaService.chat(prompt);
            List<String> parsed = Arrays.stream(aiResponse.split("\n"))
                .filter(l -> l.matches("^[1-3]\\..+"))
                .map(l -> l.replaceFirst("^[1-3]\\. ?", "").trim())
                .filter(l -> !l.isEmpty())
                .collect(Collectors.toList());
            if (!parsed.isEmpty()) recommendations = parsed;
        } catch (Exception e) {
            log.warn("AI recommendations failed: {}", e.getMessage());
        }

        return AnnualReviewResponse.builder()
            .year(year)
            .totalSpending(totalSpending)
            .estimatedIncome(income)
            .savingsRate(savingsRate)
            .interestPaid(interestPaid)
            .feesPaid(feesPaid)
            .subscriptionAnnualCost(subAnnual)
            .netWorthChange(netWorthChange)
            .categoryBreakdown(catBreakdown)
            .aiRecommendations(recommendations)
            .build();
    }
}

package com.financialguru.service;

import com.financialguru.model.Account;
import com.financialguru.model.Insight;
import com.financialguru.model.Subscription;
import com.financialguru.model.Transaction;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.InsightRepository;
import com.financialguru.repository.SubscriptionRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InsightEngineService {

    private final InsightRepository insightRepository;
    private final TransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AccountRepository accountRepository;

    public List<Insight> runAll() {
        List<Insight> all = new ArrayList<>();
        all.addAll(detectPriceIncreases());
        all.addAll(detectDuplicateCrossCard());
        all.addAll(detectSubscriptionPriceCreep());
        all.addAll(detectAtmFeeWaste());
        all.addAll(detectCategoryYoYSpike());
        all.addAll(detectBillIncreases());

        insightRepository.deleteOlderThan(OffsetDateTime.now().minusDays(90));

        return all;
    }

    private boolean isDuplicate(Insight.InsightType type, String merchant) {
        OffsetDateTime since = OffsetDateTime.now().minusDays(7);
        return !insightRepository.findRecentByTypeAndMerchant(type, merchant, since).isEmpty();
    }

    private Insight save(Insight insight) {
        return insightRepository.save(insight);
    }

    private List<Insight> detectPriceIncreases() {
        LocalDate today = LocalDate.now();
        LocalDate startThisMonth = today.withDayOfMonth(1);
        LocalDate startLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate endLastMonth = startThisMonth.minusDays(1);

        List<Object[]> thisMonthMerchants = transactionRepository.findAllTopMerchants(startThisMonth, today);
        List<Insight> insights = new ArrayList<>();

        for (Object[] row : thisMonthMerchants) {
            String merchant = (String) row[0];
            BigDecimal thisAmount = (BigDecimal) row[1];
            BigDecimal lastAmount = transactionRepository.sumMerchantSpending(merchant, startLastMonth, endLastMonth);
            if (lastAmount == null || lastAmount.compareTo(BigDecimal.ZERO) == 0) continue;
            BigDecimal change = thisAmount.subtract(lastAmount)
                .divide(lastAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            if (change.compareTo(new BigDecimal("10")) > 0) {
                if (isDuplicate(Insight.InsightType.PRICE_INCREASE, merchant)) continue;
                BigDecimal annualImpact = thisAmount.subtract(lastAmount).multiply(BigDecimal.valueOf(12));
                insights.add(save(Insight.builder()
                    .type(Insight.InsightType.PRICE_INCREASE)
                    .title("Price Increase Detected: " + merchant)
                    .description(String.format(
                        "%s charges increased %.1f%% this month ($%.2f vs $%.2f last month).",
                        merchant, change, thisAmount, lastAmount))
                    .actionText("Review if this is a price increase or one-time charge.")
                    .impactAmount(annualImpact)
                    .severity(Insight.InsightSeverity.WARNING)
                    .merchantName(merchant)
                    .build()));
            }
        }
        return insights;
    }

    private List<Insight> detectDuplicateCrossCard() {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        List<Object[]> merchants = transactionRepository.findAllTopMerchants(startOfMonth, today);
        List<Insight> insights = new ArrayList<>();

        for (Object[] row : merchants) {
            String merchant = (String) row[0];
            if (isDuplicate(Insight.InsightType.DUPLICATE_CROSS_CARD, merchant)) continue;
            List<Transaction> txns = transactionRepository.findByMerchantAndDateRange(merchant, startOfMonth, today);
            Set<UUID> accountsWithCharge = new HashSet<>();
            for (Transaction t : txns) {
                if (t.getAccount() != null) accountsWithCharge.add(t.getAccount().getId());
            }
            if (accountsWithCharge.size() >= 2) {
                insights.add(save(Insight.builder()
                    .type(Insight.InsightType.DUPLICATE_CROSS_CARD)
                    .title("Possible Duplicate Charge: " + merchant)
                    .description(String.format(
                        "%s was charged on %d different cards this month.",
                        merchant, accountsWithCharge.size()))
                    .actionText("Check if these are legitimate separate charges or duplicates.")
                    .severity(Insight.InsightSeverity.WARNING)
                    .merchantName(merchant)
                    .build()));
            }
        }
        return insights;
    }

    private List<Insight> detectSubscriptionPriceCreep() {
        if (isDuplicate(Insight.InsightType.SUBSCRIPTION_CREEP, null)) return List.of();
        LocalDate today = LocalDate.now();
        LocalDate ytdStart = today.withDayOfYear(1);
        LocalDate lastYearStart = today.minusYears(1).withDayOfYear(1);
        LocalDate lastYearEnd = ytdStart.minusDays(1);

        List<Subscription> subs = subscriptionRepository.findByIsActiveTrueOrderByAnnualCostDesc();
        BigDecimal thisYearTotal = BigDecimal.ZERO;
        BigDecimal lastYearTotal = BigDecimal.ZERO;

        for (Subscription sub : subs) {
            BigDecimal lastY = transactionRepository.sumMerchantSpending(
                sub.getMerchantName(), lastYearStart, lastYearEnd);
            BigDecimal thisY = transactionRepository.sumMerchantSpending(
                sub.getMerchantName(), ytdStart, today);
            if (lastY != null) lastYearTotal = lastYearTotal.add(lastY);
            if (thisY != null) thisYearTotal = thisYearTotal.add(thisY);
        }

        List<Insight> insights = new ArrayList<>();
        if (lastYearTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal increase = thisYearTotal.subtract(lastYearTotal);
            if (increase.compareTo(new BigDecimal("50")) > 0) {
                int dayOfYear = today.getDayOfYear();
                BigDecimal annualizedIncrease = dayOfYear > 0
                    ? increase.multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(dayOfYear), 2, RoundingMode.HALF_UP)
                    : increase;
                insights.add(save(Insight.builder()
                    .type(Insight.InsightType.SUBSCRIPTION_CREEP)
                    .title("Subscription Costs Rising")
                    .description(String.format(
                        "Your subscription spending is up $%.2f compared to same period last year.", increase))
                    .actionText("Review and cancel subscriptions you no longer use.")
                    .impactAmount(annualizedIncrease)
                    .severity(Insight.InsightSeverity.OPPORTUNITY)
                    .build()));
            }
        }
        return insights;
    }

    private List<Insight> detectAtmFeeWaste() {
        if (isDuplicate(Insight.InsightType.ATM_FEE_WASTE, null)) return List.of();
        LocalDate since = LocalDate.now().minusMonths(12);
        List<Transaction> fees = transactionRepository.findAllFeeTransactions(since);
        BigDecimal total = fees.stream()
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Insight> insights = new ArrayList<>();
        if (total.compareTo(new BigDecimal("20")) > 0) {
            insights.add(save(Insight.builder()
                .type(Insight.InsightType.ATM_FEE_WASTE)
                .title("ATM Fees Detected")
                .description(String.format("You paid $%.2f in fees over the past 12 months.", total))
                .actionText("Switch to a bank with no ATM fees or find in-network ATMs.")
                .impactAmount(total)
                .severity(Insight.InsightSeverity.OPPORTUNITY)
                .build()));
        }
        return insights;
    }

    private List<Insight> detectCategoryYoYSpike() {
        LocalDate today = LocalDate.now();
        LocalDate startThisMonth = today.withDayOfMonth(1);
        LocalDate startLastYear = today.minusYears(1).withDayOfMonth(1);
        LocalDate endLastYear = startLastYear.withDayOfMonth(startLastYear.lengthOfMonth());

        List<Object[]> categories = transactionRepository.findAllCategoryTotals(startThisMonth, today);
        List<Insight> insights = new ArrayList<>();

        for (Object[] row : categories) {
            String category = (String) row[0];
            BigDecimal thisMonthAmt = (BigDecimal) row[1];
            BigDecimal lastYearAmt = transactionRepository.sumCategorySpending(
                category, startLastYear, endLastYear);
            if (lastYearAmt == null || lastYearAmt.compareTo(BigDecimal.ZERO) == 0) continue;
            BigDecimal change = thisMonthAmt.subtract(lastYearAmt)
                .divide(lastYearAmt, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            if (change.compareTo(new BigDecimal("20")) > 0) {
                if (isDuplicate(Insight.InsightType.CATEGORY_YOY_SPIKE, category)) continue;
                insights.add(save(Insight.builder()
                    .type(Insight.InsightType.CATEGORY_YOY_SPIKE)
                    .title("Spending Spike: " + category)
                    .description(String.format(
                        "%s spending is up %.1f%% vs same month last year ($%.2f vs $%.2f).",
                        category, change, thisMonthAmt, lastYearAmt))
                    .actionText("Review what's driving the increase in " + category + " spending.")
                    .impactAmount(thisMonthAmt.subtract(lastYearAmt).multiply(BigDecimal.valueOf(12)))
                    .severity(Insight.InsightSeverity.WARNING)
                    .category(category)
                    .build()));
            }
        }
        return insights;
    }

    private List<Insight> detectBillIncreases() {
        LocalDate today = LocalDate.now();
        LocalDate startThisMonth = today.withDayOfMonth(1);
        LocalDate threeMonthsAgo = today.minusMonths(3);

        List<String> utilityCategories = List.of("UTILITIES", "PHONE", "INTERNET", "TELECOM");
        List<Insight> insights = new ArrayList<>();

        for (String cat : utilityCategories) {
            BigDecimal thisMonth = transactionRepository.sumCategorySpending(cat, startThisMonth, today);
            BigDecimal last3M = transactionRepository.sumCategorySpending(
                cat, threeMonthsAgo, startThisMonth.minusDays(1));
            if (thisMonth == null || thisMonth.compareTo(BigDecimal.ZERO) == 0) continue;
            if (last3M == null || last3M.compareTo(BigDecimal.ZERO) == 0) continue;
            BigDecimal avg3M = last3M.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            BigDecimal change = thisMonth.subtract(avg3M)
                .divide(avg3M, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            if (change.compareTo(new BigDecimal("15")) > 0) {
                if (isDuplicate(Insight.InsightType.BILL_INCREASE, cat)) continue;
                insights.add(save(Insight.builder()
                    .type(Insight.InsightType.BILL_INCREASE)
                    .title("Bill Increase: " + cat)
                    .description(String.format(
                        "%s bill increased %.1f%% vs 3-month average ($%.2f vs avg $%.2f).",
                        cat, change, thisMonth, avg3M))
                    .actionText("Call provider to negotiate or shop for better rates.")
                    .impactAmount(thisMonth.subtract(avg3M).multiply(BigDecimal.valueOf(12)))
                    .severity(Insight.InsightSeverity.WARNING)
                    .category(cat)
                    .build()));
            }
        }
        return insights;
    }
}

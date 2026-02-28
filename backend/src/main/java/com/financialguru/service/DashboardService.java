package com.financialguru.service;

import com.financialguru.dto.response.AccountResponse;
import com.financialguru.dto.response.DashboardResponse;
import com.financialguru.model.Account;
import com.financialguru.model.FinancialProfile;
import com.financialguru.model.Subscription;
import com.financialguru.model.Statement;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.StatementRepository;
import com.financialguru.repository.SubscriptionRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final AccountRepository accountRepository;
    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AlertService alertService;
    private final FinancialProfileService financialProfileService;

    // Category bucket mappings
    private static final List<String> THINGS_CATEGORIES = List.of(
        "SHOPPING", "CLOTHING", "ELECTRONICS", "HOME_IMPROVEMENT", "PERSONAL_CARE", "HOBBIES"
    );
    private static final List<String> EXPERIENCES_CATEGORIES = List.of(
        "RESTAURANTS", "DINING", "FOOD", "ENTERTAINMENT", "TRAVEL", "RECREATION", "FITNESS", "EVENTS"
    );
    private static final List<String> NECESSITIES_CATEGORIES = List.of(
        "RENT", "MORTGAGE", "HOUSING", "UTILITIES", "INSURANCE", "HEALTHCARE", "MEDICAL",
        "GAS", "AUTO", "TRANSPORTATION", "GROCERIES", "PHONE", "INTERNET", "CHILDCARE"
    );

    public DashboardResponse getDashboard() {
        List<Account> activeAccounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        List<AccountResponse> accountResponses = activeAccounts.stream()
            .map(AccountResponse::from)
            .collect(Collectors.toList());

        // Balance calculations
        BigDecimal totalCreditCardBalance = BigDecimal.ZERO;
        BigDecimal totalCreditLimit = BigDecimal.ZERO;
        BigDecimal totalCheckingBalance = BigDecimal.ZERO;
        BigDecimal totalSavingsBalance = BigDecimal.ZERO;

        for (Account a : activeAccounts) {
            if (a.getCurrentBalance() == null) continue;
            switch (a.getType()) {
                case CREDIT_CARD -> {
                    totalCreditCardBalance = totalCreditCardBalance.add(a.getCurrentBalance());
                    if (a.getCreditLimit() != null)
                        totalCreditLimit = totalCreditLimit.add(a.getCreditLimit());
                }
                case CHECKING -> totalCheckingBalance = totalCheckingBalance.add(a.getCurrentBalance());
                case SAVINGS -> totalSavingsBalance = totalSavingsBalance.add(a.getCurrentBalance());
                default -> {}
            }
        }

        BigDecimal utilizationPercent = BigDecimal.ZERO;
        if (totalCreditLimit.compareTo(BigDecimal.ZERO) > 0) {
            utilizationPercent = totalCreditCardBalance
                .divide(totalCreditLimit, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }

        BigDecimal totalAvailableCredit = totalCreditLimit.subtract(totalCreditCardBalance);

        // Monthly dates
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate startOfLastMonth = now.minusMonths(1).withDayOfMonth(1);
        LocalDate endOfLastMonth = now.withDayOfMonth(1).minusDays(1);

        BigDecimal currentMonthSpend = getGlobalSpending(startOfMonth, now);
        BigDecimal lastMonthSpend = getGlobalSpending(startOfLastMonth, endOfLastMonth);

        BigDecimal spendingChange = BigDecimal.ZERO;
        if (lastMonthSpend.compareTo(BigDecimal.ZERO) > 0) {
            spendingChange = currentMonthSpend.subtract(lastMonthSpend)
                .divide(lastMonthSpend, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }

        // YTD breakdowns
        LocalDate ytdStart = now.withDayOfYear(1);
        List<Map<String, Object>> monthlyTrend = buildMonthlyTrend();
        List<Map<String, Object>> categoryBreakdown = buildCategoryBreakdown(ytdStart, now);
        List<Map<String, Object>> topMerchants = buildTopMerchants(ytdStart, now);

        // Subscriptions
        List<Subscription> activeSubs = subscriptionRepository.findByIsActiveTrueOrderByAnnualCostDesc();
        List<Subscription> duplicateSubs = subscriptionRepository.findByIsDuplicateTrueAndIsActiveTrue();
        BigDecimal monthlySubCost = activeSubs.stream()
            .map(s -> {
                if (s.getAmount() == null) return BigDecimal.ZERO;
                return switch (s.getFrequency()) {
                    case MONTHLY -> s.getAmount();
                    case QUARTERLY -> s.getAmount().divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
                    case ANNUAL -> s.getAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
                    case WEEKLY -> s.getAmount().multiply(BigDecimal.valueOf(4.33));
                };
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Upcoming payments and expiring promos
        List<Map<String, Object>> upcomingPayments = buildUpcomingPayments(activeAccounts);
        List<Map<String, Object>> expiringPromoAprs = buildExpiringPromoAprs(activeAccounts);

        // ── Wealth Advisor KPIs ──────────────────────────────────────────────
        BigDecimal estimatedIncome = detectMonthlyIncome(startOfMonth, now);

        // Savings rate this month
        BigDecimal savingsRate = BigDecimal.ZERO;
        if (estimatedIncome.compareTo(BigDecimal.ZERO) > 0 && currentMonthSpend.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal saved = estimatedIncome.subtract(currentMonthSpend);
            savingsRate = saved.divide(estimatedIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }

        // 6-month avg savings rate
        BigDecimal avgSavingsRate = compute6MonthAvgSavingsRate();

        // Years to retirement using Mr. Money Mustache's table (5% real returns)
        Integer yearsToRetirement = computeYearsToRetirement(avgSavingsRate);

        // Freedom months = liquid assets / monthly expenses
        BigDecimal liquidAssets = totalCheckingBalance.add(totalSavingsBalance);
        BigDecimal avgMonthlyExpenses = currentMonthSpend.compareTo(BigDecimal.ZERO) > 0
            ? currentMonthSpend : lastMonthSpend;
        BigDecimal freedomMonths = BigDecimal.ZERO;
        if (avgMonthlyExpenses.compareTo(BigDecimal.ZERO) > 0) {
            freedomMonths = liquidAssets.divide(avgMonthlyExpenses, 2, RoundingMode.HALF_UP);
        }

        // Freedom trend = net cash flow this month (positive = runway growing)
        BigDecimal freedomTrend = estimatedIncome.subtract(currentMonthSpend);

        // Material (Things) spend
        BigDecimal materialThisMonth = safe(transactionRepository.sumCategoriesSpending(
            THINGS_CATEGORIES, startOfMonth, now));
        BigDecimal materialLastMonth = safe(transactionRepository.sumCategoriesSpending(
            THINGS_CATEGORIES, startOfLastMonth, endOfLastMonth));

        // Things / Experiences / Necessities breakdown this month
        BigDecimal thingsSpend = materialThisMonth;
        BigDecimal experiencesSpend = safe(transactionRepository.sumCategoriesSpending(
            EXPERIENCES_CATEGORIES, startOfMonth, now));
        BigDecimal necessitiesSpend = safe(transactionRepository.sumCategoriesSpending(
            NECESSITIES_CATEGORIES, startOfMonth, now));

        // Paycheck breakdown
        List<Map<String, Object>> paycheckBreakdown = buildPaycheckBreakdown(
            estimatedIncome, categoryBreakdown);

        return DashboardResponse.builder()
            .totalCreditCardBalance(totalCreditCardBalance)
            .totalCreditLimit(totalCreditLimit)
            .totalAvailableCredit(totalAvailableCredit)
            .overallUtilizationPercent(utilizationPercent)
            .totalCheckingBalance(totalCheckingBalance)
            .totalSavingsBalance(totalSavingsBalance)
            .unreadAlertCount(alertService.getUnreadCount())
            .recentAlerts(alertService.getRecentAlerts())
            .currentMonthSpend(currentMonthSpend)
            .lastMonthSpend(lastMonthSpend)
            .spendingChangePercent(spendingChange)
            .monthlySpendingTrend(monthlyTrend)
            .categoryBreakdown(categoryBreakdown)
            .topMerchants(topMerchants)
            .accounts(accountResponses)
            .upcomingPayments(upcomingPayments)
            .expiringPromoAprs(expiringPromoAprs)
            .monthlySubscriptionCost(monthlySubCost)
            .activeSubscriptionCount(activeSubs.size())
            .duplicateSubscriptionCount(duplicateSubs.size())
            // Wealth KPIs
            .estimatedMonthlyIncome(estimatedIncome)
            .monthlySavingsRate(savingsRate)
            .avgSavingsRate6Month(avgSavingsRate)
            .yearsToRetirementAtCurrentRate(yearsToRetirement)
            .freedomMonths(freedomMonths)
            .freedomMonthsTrend(freedomTrend)
            .materialSpendThisMonth(materialThisMonth)
            .materialSpendLastMonth(materialLastMonth)
            .thingsSpend(thingsSpend)
            .experiencesSpend(experiencesSpend)
            .necessitiesSpend(necessitiesSpend)
            .paycheckBreakdown(paycheckBreakdown)
            .build();
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal getGlobalSpending(LocalDate start, LocalDate end) {
        BigDecimal result = transactionRepository.sumAllSpending(start, end);
        return result != null ? result : BigDecimal.ZERO;
    }

    /**
     * Auto-detect monthly income: use profile if set, else sum CREDIT transactions >= $200.
     */
    private BigDecimal detectMonthlyIncome(LocalDate start, LocalDate end) {
        try {
            FinancialProfile profile = financialProfileService.getOrCreateProfile();
            if (profile.getMonthlyIncome() != null && profile.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
                return profile.getMonthlyIncome();
            }
        } catch (Exception ignored) {}
        BigDecimal detected = transactionRepository.sumIncomeAmount(
            new BigDecimal("200"), start, end);
        return detected != null ? detected : BigDecimal.ZERO;
    }

    /**
     * Compute average savings rate over last 6 full months.
     */
    private BigDecimal compute6MonthAvgSavingsRate() {
        LocalDate now = LocalDate.now();
        BigDecimal totalRate = BigDecimal.ZERO;
        int monthsWithData = 0;

        for (int i = 1; i <= 6; i++) {
            LocalDate start = now.minusMonths(i).withDayOfMonth(1);
            LocalDate end = now.minusMonths(i - 1).withDayOfMonth(1).minusDays(1);
            BigDecimal spend = getGlobalSpending(start, end);
            BigDecimal income = detectMonthlyIncome(start, end);
            if (income.compareTo(BigDecimal.ZERO) > 0 && spend.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rate = income.subtract(spend)
                    .divide(income, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                totalRate = totalRate.add(rate);
                monthsWithData++;
            }
        }

        if (monthsWithData == 0) return BigDecimal.ZERO;
        return totalRate.divide(BigDecimal.valueOf(monthsWithData), 1, RoundingMode.HALF_UP);
    }

    /**
     * Estimate years to retirement using Mr. Money Mustache's savings rate table.
     * Based on 5% real investment returns, starting from near-zero savings.
     * Returns null if savings rate <= 0.
     */
    private Integer computeYearsToRetirement(BigDecimal savingsRate) {
        if (savingsRate == null || savingsRate.compareTo(BigDecimal.ZERO) <= 0) return null;
        double sr = savingsRate.doubleValue() / 100.0;
        if (sr >= 1.0) return 0;
        // Spending fraction = 1 - sr
        // At 5% real returns, years = ln((1/sr)) / ln(1.05)
        // (rough approximation of the classic table)
        double spendFraction = 1.0 - sr;
        double years = Math.log(1.0 / sr) / Math.log(1.05) * spendFraction;
        // Clamp to reasonable range
        if (years < 0) return 0;
        if (years > 100) return null;
        return (int) Math.round(years);
    }

    private List<Map<String, Object>> buildMonthlyTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate now = LocalDate.now();

        Map<String, BigDecimal> trendMap = new LinkedHashMap<>();
        transactionRepository.findAllMonthlySpendingTrend(now.minusMonths(5).withDayOfMonth(1))
            .forEach(row -> trendMap.put((String) row[0], (BigDecimal) row[1]));

        for (int i = 5; i >= 0; i--) {
            LocalDate month = now.minusMonths(i);
            String key = month.getYear() + "-" + String.format("%02d", month.getMonthValue());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("month", key);
            entry.put("amount", trendMap.getOrDefault(key, BigDecimal.ZERO));
            trend.add(entry);
        }
        return trend;
    }

    private List<Map<String, Object>> buildCategoryBreakdown(LocalDate start, LocalDate end) {
        List<Object[]> rows = transactionRepository.findAllCategoryTotals(start, end);
        BigDecimal total = rows.stream()
            .map(r -> (BigDecimal) r[1])
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return rows.stream()
            .limit(8)
            .map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("category", r[0]);
                m.put("amount", r[1]);
                m.put("percent", total.compareTo(BigDecimal.ZERO) > 0
                    ? ((BigDecimal) r[1]).divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO);
                return m;
            })
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildTopMerchants(LocalDate start, LocalDate end) {
        return transactionRepository.findAllTopMerchants(start, end)
            .stream()
            .limit(5)
            .map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("merchant", r[0]);
                m.put("amount", r[1]);
                m.put("count", ((Long) r[2]).intValue());
                return m;
            })
            .collect(Collectors.toList());
    }

    /**
     * Build paycheck breakdown: for each category, compute % of estimated income.
     * Includes a "bucket" field: THINGS / EXPERIENCES / NECESSITIES / OTHER.
     */
    private List<Map<String, Object>> buildPaycheckBreakdown(
            BigDecimal estimatedIncome,
            List<Map<String, Object>> categoryBreakdown) {

        if (estimatedIncome.compareTo(BigDecimal.ZERO) == 0) return List.of();

        return categoryBreakdown.stream().map(cat -> {
            String category = (String) cat.get("category");
            BigDecimal amount = (BigDecimal) cat.get("amount");
            BigDecimal pctOfIncome = amount.divide(estimatedIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            String bucket = categorizeBucket(category);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", category);
            m.put("amount", amount);
            m.put("pctOfIncome", pctOfIncome);
            m.put("bucket", bucket);
            return m;
        }).collect(Collectors.toList());
    }

    private String categorizeBucket(String category) {
        if (category == null) return "OTHER";
        String upper = category.toUpperCase();
        if (THINGS_CATEGORIES.stream().anyMatch(upper::contains)) return "THINGS";
        if (EXPERIENCES_CATEGORIES.stream().anyMatch(upper::contains)) return "EXPERIENCES";
        if (NECESSITIES_CATEGORIES.stream().anyMatch(upper::contains)) return "NECESSITIES";
        return "OTHER";
    }

    private List<Map<String, Object>> buildUpcomingPayments(List<Account> accounts) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> payments = new ArrayList<>();

        for (Account a : accounts) {
            if (a.getType() != Account.AccountType.CREDIT_CARD) continue;

            LocalDate dueDate = null;
            BigDecimal minPayment = a.getMinPayment();

            List<Statement> stmts = statementRepository.findCompletedByAccountId(a.getId());
            if (!stmts.isEmpty()) {
                Statement latest = stmts.get(0);
                if (minPayment == null) minPayment = latest.getMinimumPayment();
                LocalDate stmtDue = latest.getPaymentDueDate();
                if (stmtDue != null && !stmtDue.isBefore(today)) {
                    dueDate = stmtDue;
                }
            }

            if (dueDate == null && a.getPaymentDueDay() != null) {
                int day = a.getPaymentDueDay();
                LocalDate candidate = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
                if (!candidate.isAfter(today)) {
                    LocalDate next = today.plusMonths(1);
                    candidate = next.withDayOfMonth(Math.min(day, next.lengthOfMonth()));
                }
                dueDate = candidate;
            }

            if (dueDate == null) continue;

            long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("accountId", a.getId());
            m.put("accountName", a.getName());
            m.put("dueDate", dueDate);
            m.put("daysUntilDue", daysUntilDue);
            m.put("balance", a.getCurrentBalance());
            m.put("minPayment", minPayment);
            payments.add(m);
        }

        payments.sort(Comparator.comparingLong(m -> (Long) m.get("daysUntilDue")));
        return payments;
    }

    private List<Map<String, Object>> buildExpiringPromoAprs(List<Account> accounts) {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(60);

        return accounts.stream()
            .filter(a -> a.getPromoAprEndDate() != null && a.getPromoAprEndDate().isBefore(cutoff))
            .map(a -> {
                long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, a.getPromoAprEndDate());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("accountId", a.getId());
                m.put("accountName", a.getName());
                m.put("promoApr", a.getPromoApr());
                m.put("regularApr", a.getApr());
                m.put("endDate", a.getPromoAprEndDate());
                m.put("daysLeft", daysLeft);
                m.put("balance", a.getCurrentBalance());
                return m;
            })
            .sorted(Comparator.comparingLong(m -> (Long) m.get("daysLeft")))
            .collect(Collectors.toList());
    }
}

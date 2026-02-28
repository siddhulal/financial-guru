package com.financialguru.service;

import com.financialguru.dto.response.BrainReportResponse;
import com.financialguru.dto.response.BrainReportResponse.AgeProjection;
import com.financialguru.dto.response.BrainReportResponse.ActionItem;
import com.financialguru.dto.response.BrainReportResponse.CategoryInsight;
import com.financialguru.dto.response.BrainReportResponse.ScenarioResult;
import com.financialguru.model.Account;
import com.financialguru.model.FinancialProfile;
import com.financialguru.repository.AccountRepository;
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
public class FinancialBrainService {

    private final FinancialProfileService financialProfileService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final OllamaService ollamaService;

    // 50/30/20 benchmark percentages (fraction of income)
    private static final Map<String, Double> BENCHMARKS = Map.ofEntries(
        Map.entry("HOUSING", 0.28),
        Map.entry("RENT", 0.28),
        Map.entry("GROCERIES", 0.10),
        Map.entry("FOOD", 0.10),
        Map.entry("DINING", 0.05),
        Map.entry("RESTAURANTS", 0.05),
        Map.entry("TRANSPORTATION", 0.10),
        Map.entry("AUTO", 0.10),
        Map.entry("GAS", 0.04),
        Map.entry("UTILITIES", 0.05),
        Map.entry("PHONE", 0.02),
        Map.entry("INTERNET", 0.01),
        Map.entry("HEALTHCARE", 0.05),
        Map.entry("SUBSCRIPTIONS", 0.02),
        Map.entry("ENTERTAINMENT", 0.03),
        Map.entry("SHOPPING", 0.05),
        Map.entry("CLOTHING", 0.03),
        Map.entry("FITNESS", 0.02),
        Map.entry("COFFEE", 0.01),
        Map.entry("EDUCATION", 0.05),
        Map.entry("TRAVEL", 0.05),
        Map.entry("PERSONAL_CARE", 0.02),
        Map.entry("INSURANCE", 0.05),
        Map.entry("AMAZON", 0.03),
        Map.entry("ELECTRONICS", 0.02)
    );

    public BrainReportResponse generateReport() {
        FinancialProfile profile = financialProfileService.getOrCreateProfile();

        // ─── Income ───────────────────────────────────────────────
        BigDecimal monthlyIncome = profile.getMonthlyIncome() != null && profile.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0
            ? profile.getMonthlyIncome()
            : detectIncome();

        // ─── 3-month average spend ────────────────────────────────
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        BigDecimal totalSpend3M = transactionRepository.sumAllSpending(threeMonthsAgo, LocalDate.now());
        BigDecimal monthlySpend = totalSpend3M != null && totalSpend3M.compareTo(BigDecimal.ZERO) > 0
            ? totalSpend3M.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(3000);

        // ─── Net worth from accounts ──────────────────────────────
        List<Account> accounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        BigDecimal liquid = accounts.stream()
            .filter(a -> (a.getType() == Account.AccountType.CHECKING || a.getType() == Account.AccountType.SAVINGS)
                && a.getCurrentBalance() != null)
            .map(Account::getCurrentBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ccDebt = accounts.stream()
            .filter(a -> a.getType() == Account.AccountType.CREDIT_CARD && a.getCurrentBalance() != null)
            .map(Account::getCurrentBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netWorth = liquid.subtract(ccDebt);

        BigDecimal currentInvestments = profile.getCurrentInvestments() != null
            ? profile.getCurrentInvestments() : BigDecimal.ZERO;
        BigDecimal startingPortfolio = netWorth.max(BigDecimal.ZERO).add(currentInvestments);

        // ─── Savings ──────────────────────────────────────────────
        BigDecimal monthlySavings = monthlyIncome.subtract(monthlySpend).max(BigDecimal.ZERO);
        double savingsRatePct = monthlyIncome.compareTo(BigDecimal.ZERO) > 0
            ? monthlySavings.divide(monthlyIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
            : 0.0;

        // ─── Retirement parameters ────────────────────────────────
        int age = profile.getAge() != null ? profile.getAge() : 35;
        int targetAge = profile.getTargetRetirementAge() != null ? profile.getTargetRetirementAge() : 65;
        boolean profileComplete = profile.getAge() != null;

        double annualReturn = 0.07;
        double annualSavings = monthlySavings.doubleValue() * 12;
        double fiNumber = monthlySpend.doubleValue() * 12 * 25; // 4% rule
        double portfolio = startingPortfolio.doubleValue();

        // ─── Project to target retirement age ─────────────────────
        int yearsToTarget = Math.max(1, targetAge - age);
        double portfolioAtTarget = portfolio * Math.pow(1 + annualReturn, yearsToTarget)
            + (annualSavings > 0
                ? annualSavings * (Math.pow(1 + annualReturn, yearsToTarget) - 1) / annualReturn
                : 0);

        // ─── Find actual retirement age ───────────────────────────
        int projectedRetirementAge = age + 50; // default: can't retire in 50 years
        if (portfolio >= fiNumber) {
            projectedRetirementAge = age; // already FI
        } else if (annualSavings > 0) {
            for (int n = 1; n <= 50; n++) {
                double fv = portfolio * Math.pow(1 + annualReturn, n)
                    + annualSavings * (Math.pow(1 + annualReturn, n) - 1) / annualReturn;
                if (fv >= fiNumber) {
                    projectedRetirementAge = age + n;
                    break;
                }
            }
        }

        boolean onTrack = projectedRetirementAge <= targetAge;
        int yearsEarlyOrLate = targetAge - projectedRetirementAge; // positive=early, negative=late

        // ─── Monthly gap ──────────────────────────────────────────
        BigDecimal monthlyGap = BigDecimal.ZERO;
        if (!onTrack && yearsToTarget > 0) {
            double monthlyRate = annualReturn / 12;
            double months = yearsToTarget * 12;
            double fvOfCurrent = portfolio * Math.pow(1 + annualReturn, yearsToTarget);
            double needed = (fiNumber - fvOfCurrent) * monthlyRate
                / (Math.pow(1 + monthlyRate, months) - 1);
            monthlyGap = BigDecimal.valueOf(Math.max(0, needed - monthlySavings.doubleValue()))
                .setScale(2, RoundingMode.HALF_UP);
        }

        // ─── Readiness score ──────────────────────────────────────
        int readinessScore = calcReadinessScore(portfolioAtTarget, fiNumber, savingsRatePct,
            projectedRetirementAge, targetAge);
        String readinessGrade = readinessScore >= 85 ? "A" : readinessScore >= 70 ? "B"
            : readinessScore >= 55 ? "C" : readinessScore >= 40 ? "D" : "F";

        // ─── Headline ─────────────────────────────────────────────
        String headline;
        if (!profileComplete) {
            headline = "Set your age in your profile to see your retirement projection";
        } else if (projectedRetirementAge <= age) {
            headline = "You are already financially independent!";
        } else if (onTrack) {
            headline = String.format("On track to retire at %d — %d years early!",
                projectedRetirementAge, yearsEarlyOrLate);
        } else {
            headline = String.format("At current rate, you'll retire at %d — %d years later than your goal of %d",
                projectedRetirementAge, Math.abs(yearsEarlyOrLate), targetAge);
        }

        // ─── Year-by-year projections ─────────────────────────────
        List<AgeProjection> currentPath = buildProjections(age, 85, portfolio, annualSavings, annualReturn);
        double optimalAnnualSavings = monthlyIncome.doubleValue() * 0.20 * 12;
        List<AgeProjection> optimalPath = buildProjections(age, 85, portfolio, optimalAnnualSavings, annualReturn);

        // ─── Scenarios ────────────────────────────────────────────
        List<ScenarioResult> scenarios = buildScenarios(age, targetAge, portfolio,
            monthlySavings.doubleValue(), fiNumber, annualReturn, projectedRetirementAge);

        // ─── Category insights ────────────────────────────────────
        List<CategoryInsight> categories = buildCategoryInsights(monthlyIncome);

        // ─── Action roadmap ───────────────────────────────────────
        List<ActionItem> actions = buildActionRoadmap(categories, monthlySavings.doubleValue(),
            projectedRetirementAge, targetAge, age, annualReturn, fiNumber, portfolio);

        // ─── AI narrative ─────────────────────────────────────────
        boolean aiAvailable = false;
        String narrative = buildFallbackNarrative(age, targetAge, monthlyIncome, monthlySpend,
            monthlySavings, savingsRatePct, fiNumber, portfolioAtTarget, projectedRetirementAge, monthlyGap);
        try {
            String prompt = buildOllamaPrompt(age, targetAge, monthlyIncome, monthlySpend,
                monthlySavings, savingsRatePct, netWorth, fiNumber, portfolioAtTarget,
                projectedRetirementAge, monthlyGap, categories);
            String aiResponse = ollamaService.chat(
                "You are a sharp, direct personal financial advisor. Be specific with numbers. No generic advice.",
                prompt);
            if (aiResponse != null && aiResponse.length() > 50) {
                narrative = aiResponse;
                aiAvailable = true;
            }
        } catch (Exception e) {
            log.warn("Ollama unavailable for brain report: {}", e.getMessage());
        }

        return BrainReportResponse.builder()
            .age(age)
            .targetRetirementAge(targetAge)
            .monthlyIncome(monthlyIncome)
            .monthlySpend(monthlySpend)
            .monthlySavings(monthlySavings)
            .savingsRatePct(savingsRatePct)
            .netWorth(netWorth)
            .currentInvestments(currentInvestments)
            .profileComplete(profileComplete)
            .fiNumber(BigDecimal.valueOf(fiNumber).setScale(0, RoundingMode.HALF_UP))
            .projectedCorpusAtTargetAge(portfolioAtTarget)
            .projectedRetirementAge(projectedRetirementAge)
            .onTrack(onTrack)
            .yearsEarlyOrLate(yearsEarlyOrLate)
            .monthlyGapToTarget(monthlyGap)
            .retirementReadinessScore(readinessScore)
            .retirementReadinessGrade(readinessGrade)
            .trajectoryHeadline(headline)
            .currentPathProjections(currentPath)
            .optimalPathProjections(optimalPath)
            .scenarios(scenarios)
            .topCategories(categories)
            .actionRoadmap(actions)
            .aiNarrative(narrative)
            .aiAvailable(aiAvailable)
            .build();
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private int calcReadinessScore(double portfolioAtTarget, double fiNumber, double savingsRate,
                                    int projectedAge, int targetAge) {
        int score = 0;
        // 40 pts: portfolio coverage
        double coverage = fiNumber > 0 ? portfolioAtTarget / fiNumber : 0;
        score += (int) Math.min(40, coverage * 40);
        // 30 pts: savings rate
        if (savingsRate >= 20) score += 30;
        else if (savingsRate >= 15) score += 22;
        else if (savingsRate >= 10) score += 15;
        else if (savingsRate >= 5) score += 8;
        // 30 pts: retire on time
        int diff = targetAge - projectedAge;
        if (diff >= 0) score += 30;
        else if (diff >= -5) score += 18;
        else if (diff >= -10) score += 10;
        else score += 3;
        return Math.min(100, score);
    }

    private List<AgeProjection> buildProjections(int currentAge, int toAge, double portfolio,
                                                   double annualSavings, double annualReturn) {
        List<AgeProjection> list = new ArrayList<>();
        double val = portfolio;
        int currentYear = LocalDate.now().getYear();
        for (int a = currentAge; a <= toAge; a++) {
            list.add(new AgeProjection(a, currentYear + (a - currentAge),
                BigDecimal.valueOf(Math.max(0, val)).setScale(0, RoundingMode.HALF_UP)));
            val = val * (1 + annualReturn) + annualSavings;
        }
        return list;
    }

    private List<ScenarioResult> buildScenarios(int age, int targetAge, double portfolio,
                                                  double currentMonthlySavings, double fiNumber,
                                                  double annualReturn, int currentProjectedAge) {
        List<ScenarioResult> results = new ArrayList<>();
        double[] extras = {200, 500, 1000};
        String[] colors = {"gold", "green", "blue"};
        for (int i = 0; i < extras.length; i++) {
            double extra = extras[i];
            double newAnnual = (currentMonthlySavings + extra) * 12;
            int newRetirementAge = age + 50;
            if (portfolio >= fiNumber) {
                newRetirementAge = age;
            } else {
                for (int n = 1; n <= 50; n++) {
                    double fv = portfolio * Math.pow(1 + annualReturn, n)
                        + newAnnual * (Math.pow(1 + annualReturn, n) - 1) / annualReturn;
                    if (fv >= fiNumber) { newRetirementAge = age + n; break; }
                }
            }
            double yearsToTarget = Math.max(1, targetAge - age);
            double portfolioAtTarget = portfolio * Math.pow(1 + annualReturn, yearsToTarget)
                + newAnnual * (Math.pow(1 + annualReturn, yearsToTarget) - 1) / annualReturn;
            int yearsEarlier = currentProjectedAge - newRetirementAge;
            String headline = yearsEarlier > 0
                ? String.format("Retire at %d — %d year%s earlier", newRetirementAge, yearsEarlier, yearsEarlier == 1 ? "" : "s")
                : String.format("Retire at %d (same timeline)", newRetirementAge);
            results.add(new ScenarioResult(
                "save_" + (int) extra,
                String.format("Save $%,.0f more/month", extra),
                BigDecimal.valueOf(extra),
                newRetirementAge,
                yearsEarlier,
                BigDecimal.valueOf(portfolioAtTarget).setScale(0, RoundingMode.HALF_UP),
                headline,
                colors[i]
            ));
        }
        return results;
    }

    private List<CategoryInsight> buildCategoryInsights(BigDecimal monthlyIncome) {
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        List<Object[]> rows = transactionRepository.findAllCategoryTotals(threeMonthsAgo, LocalDate.now());
        List<CategoryInsight> insights = new ArrayList<>();
        double income = monthlyIncome.doubleValue();
        for (Object[] row : rows) {
            String cat = (String) row[0];
            BigDecimal total = (BigDecimal) row[1];
            BigDecimal monthly = total.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            double pct = income > 0 ? monthly.doubleValue() / income : 0;
            double benchmark = BENCHMARKS.getOrDefault(cat.toUpperCase(), 0.05);
            String status = pct > benchmark * 1.2 ? "OVER" : pct > benchmark * 0.8 ? "OK" : "GOOD";
            // 10-year retirement impact: monthly overspend × 12 × FV annuity factor at 7% for 10yr
            double monthlyOverspend = Math.max(0, monthly.doubleValue() - income * benchmark);
            double fvFactor = (Math.pow(1.07, 10) - 1) / (0.07 / 12); // monthly contribution FV factor
            BigDecimal retirementImpact = BigDecimal.valueOf(monthlyOverspend * fvFactor)
                .setScale(0, RoundingMode.HALF_UP);
            insights.add(new CategoryInsight(
                cat,
                monthly,
                Math.round(pct * 1000.0) / 10.0,
                benchmark * 100,
                status,
                monthly.multiply(BigDecimal.valueOf(12)),
                retirementImpact
            ));
        }
        insights.sort(Comparator.comparing(CategoryInsight::getMonthlyAvg).reversed());
        return insights.stream().limit(10).collect(Collectors.toList());
    }

    private List<ActionItem> buildActionRoadmap(List<CategoryInsight> categories,
                                                  double currentMonthlySavings,
                                                  int projectedRetirementAge, int targetAge,
                                                  int age, double annualReturn, double fiNumber,
                                                  double portfolio) {
        List<ActionItem> actions = new ArrayList<>();
        int rank = 1;

        // Top over-benchmark categories
        for (CategoryInsight cat : categories) {
            if (!"OVER".equals(cat.getStatus())) continue;
            double benchmarkAmt = cat.getMonthlyAvg().doubleValue() * (cat.getBenchmarkPct() / cat.getPctOfIncome());
            double saving = cat.getMonthlyAvg().doubleValue() - benchmarkAmt;
            if (saving < 15) continue;

            // How many years earlier does this saving bring retirement?
            double newAnnual = (currentMonthlySavings + saving) * 12;
            int newAge = age + 50;
            for (int n = 1; n <= 50; n++) {
                double fv = portfolio * Math.pow(1 + annualReturn, n)
                    + newAnnual * (Math.pow(1 + annualReturn, n) - 1) / annualReturn;
                if (fv >= fiNumber) { newAge = age + n; break; }
            }

            actions.add(new ActionItem(
                rank++,
                "Reduce " + capitalize(cat.getCategory()) + " spending",
                String.format("Cut from $%.0f to $%.0f/month (benchmark: %.0f%% of income). Saves $%.0f/month.",
                    cat.getMonthlyAvg().doubleValue(), benchmarkAmt, cat.getBenchmarkPct(), saving),
                BigDecimal.valueOf(saving).setScale(2, RoundingMode.HALF_UP),
                projectedRetirementAge - newAge,
                "SPENDING"
            ));
            if (rank > 5) break;
        }

        // Always add: increase savings rate action
        if (rank <= 6) {
            actions.add(new ActionItem(
                rank++,
                "Automate savings transfer on payday",
                "Set up an auto-transfer to a high-yield savings or investment account the day your paycheck lands. Even $200/month invested at 7% = $104K in 20 years.",
                BigDecimal.valueOf(200),
                0,
                "SAVING"
            ));
        }

        // Debt payoff if CC debt exists
        boolean hasCCDebt = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
            .anyMatch(a -> a.getType() == Account.AccountType.CREDIT_CARD
                && a.getCurrentBalance() != null
                && a.getCurrentBalance().compareTo(BigDecimal.valueOf(1000)) > 0);
        if (hasCCDebt && rank <= 7) {
            actions.add(new ActionItem(
                rank,
                "Eliminate credit card debt",
                "CC interest (18-25% APR) destroys wealth faster than almost anything. Pay off balances before investing beyond your employer match.",
                BigDecimal.ZERO,
                1,
                "DEBT"
            ));
        }

        return actions;
    }

    private String buildOllamaPrompt(int age, int targetAge, BigDecimal income, BigDecimal spend,
                                      BigDecimal savings, double savingsRate, BigDecimal netWorth,
                                      double fiNumber, double portfolioAtTarget,
                                      int projectedRetirementAge, BigDecimal monthlyGap,
                                      List<CategoryInsight> categories) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze this person's finances and give them a direct, personalized 4-paragraph assessment.\n\n");
        sb.append(String.format("AGE: %d | TARGET RETIREMENT AGE: %d\n", age, targetAge));
        sb.append(String.format("MONTHLY INCOME: $%.0f | MONTHLY SPEND: $%.0f | MONTHLY SAVINGS: $%.0f (%.1f%% rate)\n",
            income.doubleValue(), spend.doubleValue(), savings.doubleValue(), savingsRate));
        sb.append(String.format("CURRENT NET WORTH: $%.0f\n", netWorth.doubleValue()));
        sb.append(String.format("FI NUMBER (retirement target): $%.0f\n", fiNumber));
        sb.append(String.format("PROJECTED CORPUS AT AGE %d: $%.0f\n", targetAge, portfolioAtTarget));
        sb.append(String.format("PROJECTED RETIREMENT AGE (current rate): %d\n", projectedRetirementAge));
        if (monthlyGap.compareTo(BigDecimal.ZERO) > 0)
            sb.append(String.format("MONTHLY GAP TO RETIRE ON TIME: $%.0f more needed per month\n", monthlyGap.doubleValue()));
        sb.append("\nTOP SPENDING CATEGORIES (monthly avg, last 3 months):\n");
        categories.stream().limit(6).forEach(c ->
            sb.append(String.format("  %s: $%.0f/mo (%.1f%% of income, benchmark: %.0f%%) -- %s\n",
                c.getCategory(), c.getMonthlyAvg().doubleValue(), c.getPctOfIncome(),
                c.getBenchmarkPct(), c.getStatus())));
        sb.append("\nWrite 4 paragraphs:\n");
        sb.append("1. Where they stand right now (be direct about whether this is good or bad)\n");
        sb.append("2. What retirement looks like at current trajectory (specific numbers)\n");
        sb.append("3. The single biggest change that would help most (cite exact dollar amounts)\n");
        sb.append("4. One specific thing they can do TODAY to improve their situation\n");
        sb.append("Be encouraging but brutally honest. Use their actual numbers throughout.");
        return sb.toString();
    }

    private String buildFallbackNarrative(int age, int targetAge, BigDecimal income, BigDecimal spend,
                                           BigDecimal savings, double savingsRate, double fiNumber,
                                           double portfolioAtTarget, int projectedRetirementAge,
                                           BigDecimal monthlyGap) {
        double coverage = fiNumber > 0 ? (portfolioAtTarget / fiNumber) * 100 : 0;
        // 10-year projection for current savings
        double fv10yr = savings.doubleValue() * 12 * ((Math.pow(1.07, 10) - 1) / 0.07);
        return String.format(
            "At your current savings rate of %.1f%% ($%.0f/month), you are on track to build a portfolio of $%.0f by age %d " +
            "-- covering %.0f%% of your retirement target of $%.0f (which is 25x your annual expenses).\n\n" +
            "%s\n\n" +
            "Your 3-month average spending is $%.0f/month. " +
            "If you invest your current savings at 7%% annually for the next 10 years, that alone grows to $%.0f. " +
            "The most powerful lever you have right now is your savings rate -- every 1%% increase adds roughly 1-2 years of freedom.\n\n" +
            "Set your age and retirement goal in your profile above for a fully personalized AI analysis with specific action steps.",
            savingsRate, savings.doubleValue(), portfolioAtTarget, targetAge,
            coverage, fiNumber,
            projectedRetirementAge <= targetAge
                ? String.format("You are projected to reach financial independence at age %d -- %d years ahead of your goal. Keep the momentum.", projectedRetirementAge, targetAge - projectedRetirementAge)
                : String.format("To retire at your target age of %d instead of %d, you need to save an additional $%.0f/month. " +
                  "That's a gap of %.1f%% of your income -- achievable through targeted spending cuts in your top categories.",
                  targetAge, projectedRetirementAge, monthlyGap.doubleValue(),
                  income.compareTo(BigDecimal.ZERO) > 0 ? monthlyGap.divide(income, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue() : 0),
            spend.doubleValue(), fv10yr
        );
    }

    private BigDecimal detectIncome() {
        LocalDate since = LocalDate.now().minusMonths(3);
        BigDecimal total = transactionRepository.sumIncomeAmount(new BigDecimal("500"), since, LocalDate.now());
        return total != null && total.compareTo(BigDecimal.ZERO) > 0
            ? total.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(4000);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase().replace("_", " ");
    }
}

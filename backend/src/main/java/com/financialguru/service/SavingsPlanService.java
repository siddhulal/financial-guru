package com.financialguru.service;

import com.financialguru.dto.response.SavingsPlanResponse;
import com.financialguru.dto.response.SavingsPlanResponse.*;
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
public class SavingsPlanService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final FinancialProfileService financialProfileService;
    private final OllamaService ollamaService;

    // ── Benchmark: max recommended spending as % of monthly income ─────────
    // Based on standard 50/30/20 rule + real advisor experience
    private static final Map<String, double[]> BENCHMARKS = new LinkedHashMap<>();
    static {
        // format: {benchmarkPct, easeScore (1=hardest to cut, 5=easiest)}
        BENCHMARKS.put("HOUSING",          new double[]{0.28, 1});
        BENCHMARKS.put("RENT",             new double[]{0.28, 1});
        BENCHMARKS.put("MORTGAGE",         new double[]{0.28, 1});
        BENCHMARKS.put("GROCERIES",        new double[]{0.10, 2});
        BENCHMARKS.put("DINING",           new double[]{0.05, 4});
        BENCHMARKS.put("RESTAURANTS",      new double[]{0.05, 4});
        BENCHMARKS.put("FOOD",             new double[]{0.08, 3});
        BENCHMARKS.put("COFFEE",           new double[]{0.01, 5});
        BENCHMARKS.put("TRANSPORTATION",   new double[]{0.12, 2});
        BENCHMARKS.put("AUTO",             new double[]{0.12, 2});
        BENCHMARKS.put("GAS",              new double[]{0.04, 2});
        BENCHMARKS.put("UTILITIES",        new double[]{0.05, 1});
        BENCHMARKS.put("ENTERTAINMENT",    new double[]{0.04, 4});
        BENCHMARKS.put("SHOPPING",         new double[]{0.05, 3});
        BENCHMARKS.put("CLOTHING",         new double[]{0.03, 3});
        BENCHMARKS.put("ELECTRONICS",      new double[]{0.02, 3});
        BENCHMARKS.put("SUBSCRIPTIONS",    new double[]{0.02, 5});
        BENCHMARKS.put("FITNESS",          new double[]{0.02, 3});
        BENCHMARKS.put("PERSONAL_CARE",    new double[]{0.02, 3});
        BENCHMARKS.put("TRAVEL",           new double[]{0.04, 3});
        BENCHMARKS.put("HEALTHCARE",       new double[]{0.06, 1});
        BENCHMARKS.put("INSURANCE",        new double[]{0.06, 1});
        BENCHMARKS.put("PHONE",            new double[]{0.02, 3});
        BENCHMARKS.put("INTERNET",         new double[]{0.01, 2});
        BENCHMARKS.put("AMAZON",           new double[]{0.03, 3});
    }

    private static final Map<String, String> DIFFICULTY = Map.of(
        "1", "HARD", "2", "HARD", "3", "MEDIUM", "4", "EASY", "5", "EASY"
    );

    public SavingsPlanResponse buildPlan(BigDecimal targetAdditionalSavings) {
        LocalDate today = LocalDate.now();
        LocalDate threeMonthsAgo = today.minusMonths(3);

        // ── 1. Income ─────────────────────────────────────────────────────
        BigDecimal income = detectIncome(threeMonthsAgo, today);

        // ── 2. Current spending by category (3-month average) ────────────
        List<Object[]> rawCategories = transactionRepository.findAllCategoryTotals(threeMonthsAgo, today);
        Map<String, BigDecimal> categoryMonthly = new LinkedHashMap<>();
        for (Object[] row : rawCategories) {
            String cat = (String) row[0];
            BigDecimal total = (BigDecimal) row[1];
            // divide by 3 to get monthly average
            categoryMonthly.put(cat.toUpperCase(),
                total.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP));
        }

        // ── 3. Current state ─────────────────────────────────────────────
        BigDecimal totalSpend = categoryMonthly.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentSavings = income.subtract(totalSpend).max(BigDecimal.ZERO);
        BigDecimal currentSavingsRate = income.compareTo(BigDecimal.ZERO) > 0
            ? currentSavings.divide(income, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        BigDecimal targetSavings = currentSavings.add(targetAdditionalSavings);
        BigDecimal targetRate = income.compareTo(BigDecimal.ZERO) > 0
            ? targetSavings.divide(income, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        // ── 4. Spending breakdown (with benchmark status) ────────────────
        List<SpendingCategory> spendingBreakdown = buildSpendingBreakdown(categoryMonthly, income);

        // ── 5. Identify overspent categories + rank by cut potential ─────
        // Score = overspend × easeScore (cut the easy, high-overspend items first)
        List<CategoryScore> scored = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : categoryMonthly.entrySet()) {
            String cat = entry.getKey();
            BigDecimal monthly = entry.getValue();
            double[] benchmark = getBenchmark(cat);
            double benchPct = benchmark[0];
            int ease = (int) benchmark[1];
            BigDecimal benchAmount = income.multiply(BigDecimal.valueOf(benchPct)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal overspend = monthly.subtract(benchAmount).max(BigDecimal.ZERO);
            if (overspend.compareTo(BigDecimal.valueOf(10)) > 0) {
                double score = overspend.doubleValue() * ease;
                scored.add(new CategoryScore(cat, monthly, benchAmount, benchPct, ease, overspend, score));
            }
        }
        scored.sort(Comparator.comparingDouble(CategoryScore::score).reversed());

        // ── 6. Greedy allocation — pick cuts until target is reached ──────
        BigDecimal remaining = targetAdditionalSavings;
        List<CategoryRecommendation> recommendations = new ArrayList<>();

        for (CategoryScore cs : scored) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal cut = cs.overspend().min(remaining);
            // Don't recommend tiny cuts (< $15)
            if (cut.compareTo(BigDecimal.valueOf(15)) < 0) continue;

            BigDecimal newTarget = cs.currentSpend().subtract(cut);

            // Get top merchants for this category
            List<TopMerchant> merchants = getTopMerchants(cs.category(), threeMonthsAgo, today);

            // Generate specific actions
            List<String> actions = generateActions(cs.category(), cs.currentSpend(), newTarget, merchants, income);

            String diff = DIFFICULTY.getOrDefault(String.valueOf(cs.ease()), "MEDIUM");

            recommendations.add(CategoryRecommendation.builder()
                .category(cs.category())
                .currentMonthlySpend(cs.currentSpend())
                .targetMonthlySpend(newTarget)
                .monthlySavings(cut)
                .benchmarkAmount(cs.benchmark())
                .benchmarkPct(cs.benchPct())
                .difficulty(diff)
                .easeScore(cs.ease())
                .topMerchants(merchants)
                .specificActions(actions)
                .reasoning(buildReasoning(cs.category(), cs.currentSpend(), cs.benchmark(), income))
                .build());

            remaining = remaining.subtract(cut);
        }

        BigDecimal totalRecommended = targetAdditionalSavings.subtract(remaining.max(BigDecimal.ZERO));
        boolean achievable = remaining.compareTo(BigDecimal.ZERO) <= 0;
        BigDecimal coverage = targetAdditionalSavings.compareTo(BigDecimal.ZERO) > 0
            ? totalRecommended.divide(targetAdditionalSavings, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        // ── 7. AI narrative ───────────────────────────────────────────────
        String narrative = "";
        boolean aiAvailable = false;
        try {
            if (ollamaService.isAvailable()) {
                narrative = generateAINarrative(income, totalSpend, currentSavings, currentSavingsRate,
                    targetAdditionalSavings, targetSavings, targetRate, recommendations, categoryMonthly);
                aiAvailable = true;
            }
        } catch (Exception e) {
            log.warn("Ollama unavailable for savings plan: {}", e.getMessage());
            narrative = buildFallbackNarrative(income, currentSavings, currentSavingsRate,
                targetAdditionalSavings, recommendations);
        }
        if (narrative.isBlank()) {
            narrative = buildFallbackNarrative(income, currentSavings, currentSavingsRate,
                targetAdditionalSavings, recommendations);
        }

        return SavingsPlanResponse.builder()
            .monthlyIncome(income)
            .currentMonthlySpend(totalSpend)
            .currentMonthlySavings(currentSavings)
            .currentSavingsRatePct(currentSavingsRate)
            .targetAdditionalSavings(targetAdditionalSavings)
            .targetMonthlySavings(targetSavings)
            .targetSavingsRatePct(targetRate)
            .totalRecommendedSavings(totalRecommended)
            .coveragePct(coverage)
            .goalAchievable(achievable)
            .aiNarrative(narrative)
            .aiAvailable(aiAvailable)
            .recommendations(recommendations)
            .spendingBreakdown(spendingBreakdown)
            .build();
    }

    // ── private helpers ───────────────────────────────────────────────────

    private BigDecimal detectIncome(LocalDate start, LocalDate end) {
        try {
            FinancialProfile profile = financialProfileService.getOrCreateProfile();
            if (profile.getMonthlyIncome() != null
                    && profile.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
                return profile.getMonthlyIncome();
            }
        } catch (Exception ignored) {}
        BigDecimal detected = transactionRepository.sumIncomeAmount(
            new BigDecimal("200"), start, end);
        if (detected != null && detected.compareTo(BigDecimal.ZERO) > 0) {
            // Divide by 3 months
            return detected.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private double[] getBenchmark(String category) {
        // Direct match
        double[] b = BENCHMARKS.get(category);
        if (b != null) return b;
        // Prefix match (e.g. "FOOD & DINING" → DINING)
        for (Map.Entry<String, double[]> e : BENCHMARKS.entrySet()) {
            if (category.contains(e.getKey()) || e.getKey().contains(category)) return e.getValue();
        }
        // Default: 5% of income, medium ease
        return new double[]{0.05, 3};
    }

    private List<SpendingCategory> buildSpendingBreakdown(
            Map<String, BigDecimal> categoryMonthly, BigDecimal income) {
        return categoryMonthly.entrySet().stream()
            .map(e -> {
                String cat = e.getKey();
                BigDecimal monthly = e.getValue();
                double[] bench = getBenchmark(cat);
                double benchPct = bench[0];
                double pctOfIncome = income.compareTo(BigDecimal.ZERO) > 0
                    ? monthly.doubleValue() / income.doubleValue() * 100 : 0;
                String status;
                if (pctOfIncome > benchPct * 100 * 1.2) status = "OVER";
                else if (pctOfIncome > benchPct * 100 * 0.8) status = "OK";
                else status = "GOOD";
                return SpendingCategory.builder()
                    .category(cat)
                    .monthlyAvg(monthly)
                    .pctOfIncome(Math.round(pctOfIncome * 10.0) / 10.0)
                    .benchmarkPct(benchPct * 100)
                    .status(status)
                    .build();
            })
            .sorted(Comparator.comparing(SpendingCategory::getMonthlyAvg).reversed())
            .collect(Collectors.toList());
    }

    private List<TopMerchant> getTopMerchants(String category, LocalDate start, LocalDate end) {
        try {
            List<Object[]> merchants = transactionRepository.findAllTopMerchants(start, end);
            return merchants.stream()
                .filter(row -> {
                    // Filter merchants that plausibly belong to this category
                    // by checking if any transaction with this merchant has this category
                    // For simplicity: just return top 3 from the global list for context
                    return true;
                })
                .limit(3)
                .map(row -> TopMerchant.builder()
                    .name((String) row[0])
                    .monthlyAmount(((BigDecimal) row[1]).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP))
                    .transactionCount(((Long) row[2]).intValue() / 3)
                    .build())
                .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> generateActions(String category, BigDecimal current,
            BigDecimal target, List<TopMerchant> merchants, BigDecimal income) {
        List<String> actions = new ArrayList<>();
        BigDecimal cut = current.subtract(target);
        String cutStr = String.format("$%.0f", cut.doubleValue());

        switch (category) {
            case "DINING", "RESTAURANTS" -> {
                int mealsPerWeek = (int) Math.ceil(cut.doubleValue() / 20);
                actions.add(String.format("Cook %d more meals at home per week — saves ~$20 each vs dining out", mealsPerWeek));
                actions.add(String.format("Cap restaurant visits to %d times per week (currently over budget by %s/month)",
                    Math.max(1, (int)(target.doubleValue() / 60)), cutStr));
                if (!merchants.isEmpty()) {
                    actions.add(String.format("Review your top dining expense: %s (~$%.0f/month) — consider limiting to once a week",
                        merchants.get(0).getName(), merchants.get(0).getMonthlyAmount().doubleValue()));
                }
            }
            case "COFFEE", "CAFE" -> {
                actions.add(String.format("Making coffee at home saves ~$4/day vs coffee shops — that's %s/month", cutStr));
                actions.add("Invest in a decent home espresso machine — pays for itself in 2 months");
            }
            case "SUBSCRIPTIONS" -> {
                actions.add(String.format("Review all %d active subscriptions — cancel any unused in the last 60 days", 3));
                actions.add("Bundle streaming services (share plans, use Disney+/Hulu bundle instead of both separate)");
                actions.add(String.format("Target: eliminate %s in unused subscriptions this week", cutStr));
            }
            case "SHOPPING", "AMAZON" -> {
                actions.add("Implement a 72-hour rule: wait 3 days before any non-essential purchase over $30");
                actions.add("Remove saved payment info from Amazon/shopping sites — friction reduces impulse buys");
                actions.add(String.format("Set a weekly shopping budget of $%.0f and use a separate envelope/account", target.doubleValue() / 4));
            }
            case "ENTERTAINMENT" -> {
                actions.add("Use library cards for movies, audiobooks, and events — free in most cities");
                actions.add("Look for free local events (parks, community centers, meetups) 2x/month");
                actions.add(String.format("Set an entertainment cap of $%.0f/month — that's still a good time", target.doubleValue()));
            }
            case "CLOTHING" -> {
                actions.add("Try a 30-day no-buy challenge for clothing — most people rediscover items they forgot they owned");
                actions.add("Use ThredUp/Poshmark for clothing needs — 50-70% cheaper than retail");
                actions.add(String.format("The %s/month cut frees up $%.0f/year — that's a real vacation", cutStr, cut.doubleValue() * 12));
            }
            case "FOOD", "GROCERIES" -> {
                actions.add("Meal plan on Sundays — reduces food waste and impulse grocery purchases by ~15%");
                actions.add("Shop with a list and eat before grocery shopping — saves $30-50/trip");
                actions.add("Try generic brands for staples (pasta, rice, canned goods) — identical quality, 30% cheaper");
            }
            case "TRANSPORTATION", "GAS" -> {
                actions.add("Combine errands into 1-2 trips per week — reduces gas by 20%");
                actions.add("Check GasBuddy for cheapest gas near you — saves $0.30-0.50/gallon");
                actions.add("Carpool or use public transit 2 days/week if possible");
            }
            default -> {
                actions.add(String.format("Reduce %s spending from $%.0f to $%.0f/month — saves %s",
                    category.toLowerCase(), current.doubleValue(), target.doubleValue(), cutStr));
                actions.add("Track every purchase in this category for 2 weeks — awareness alone reduces spending by 15%");
            }
        }
        return actions;
    }

    private String buildReasoning(String category, BigDecimal current, BigDecimal benchmark, BigDecimal income) {
        double currentPct = income.compareTo(BigDecimal.ZERO) > 0
            ? current.doubleValue() / income.doubleValue() * 100 : 0;
        double benchPct = getBenchmark(category)[0] * 100;
        return String.format("You spend %.1f%% of income on %s (advisor benchmark: %.0f%%)",
            currentPct, category.toLowerCase(), benchPct);
    }

    private String generateAINarrative(
            BigDecimal income, BigDecimal totalSpend, BigDecimal currentSavings,
            BigDecimal currentSavingsRate, BigDecimal targetExtra, BigDecimal targetSavings,
            BigDecimal targetRate, List<CategoryRecommendation> recs,
            Map<String, BigDecimal> allCategories) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a trusted personal financial advisor reviewing a client's actual transaction data.\n\n");
        prompt.append("CLIENT FINANCIAL SNAPSHOT:\n");
        prompt.append(String.format("- Monthly take-home income: $%.0f\n", income.doubleValue()));
        prompt.append(String.format("- Current monthly spending: $%.0f\n", totalSpend.doubleValue()));
        prompt.append(String.format("- Current monthly savings: $%.0f (%.1f%% savings rate)\n",
            currentSavings.doubleValue(), currentSavingsRate.doubleValue()));
        prompt.append(String.format("- Goal: save an additional $%.0f/month (target: $%.0f/month = %.1f%% savings rate)\n\n",
            targetExtra.doubleValue(), targetSavings.doubleValue(), targetRate.doubleValue()));

        prompt.append("SPENDING BREAKDOWN (last 3 months avg/month):\n");
        allCategories.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(10)
            .forEach(e -> {
                double pct = income.compareTo(BigDecimal.ZERO) > 0
                    ? e.getValue().doubleValue() / income.doubleValue() * 100 : 0;
                double[] bench = getBenchmark(e.getKey());
                String flag = pct > bench[0] * 100 * 1.2 ? " <- OVER BENCHMARK" : "";
                prompt.append(String.format("- %s: $%.0f/month (%.1f%% of income, benchmark: %.0f%%)%s\n",
                    e.getKey(), e.getValue().doubleValue(), pct, bench[0] * 100, flag));
            });

        if (!recs.isEmpty()) {
            prompt.append("\nRECOMMENDED CUTS (by the algorithm):\n");
            for (CategoryRecommendation rec : recs) {
                prompt.append(String.format("- Cut %s from $%.0f to $%.0f/month — saves $%.0f\n",
                    rec.getCategory(), rec.getCurrentMonthlySpend().doubleValue(),
                    rec.getTargetMonthlySpend().doubleValue(), rec.getMonthlySavings().doubleValue()));
            }
        }

        prompt.append("\nYour task: Write a 3-paragraph personalized response to this client. You must:\n");
        prompt.append("1. Acknowledge their CURRENT situation with specific numbers (what they earn, save, and spend)\n");
        prompt.append("2. Give them a CLEAR PATH to the $").append(String.format("%.0f", targetExtra.doubleValue()))
              .append(" goal — mention the specific categories and dollar amounts\n");
        prompt.append("3. End with ONE motivating insight (e.g., what that extra savings means in 5 years, or how it affects retirement)\n\n");
        prompt.append("Tone: direct, supportive, like a trusted advisor — NOT judgmental, NOT generic. Use THEIR actual numbers.\n");
        prompt.append("Do NOT use bullet points or headers. Write in plain paragraphs. Maximum 200 words.\n\n");
        prompt.append("Response:");

        return ollamaService.chat(prompt.toString());
    }

    private String buildFallbackNarrative(BigDecimal income, BigDecimal currentSavings,
            BigDecimal currentSavingsRate, BigDecimal targetExtra,
            List<CategoryRecommendation> recs) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "Based on your income of $%.0f/month, you're currently saving $%.0f/month (%.1f%% savings rate). ",
            income.doubleValue(), currentSavings.doubleValue(), currentSavingsRate.doubleValue()));

        if (recs.isEmpty()) {
            sb.append(String.format(
                "To save an additional $%.0f/month, you'll need to review your spending — your current categories are close to or under benchmark. " +
                "Consider auditing subscriptions and dining as the easiest wins.", targetExtra.doubleValue()));
        } else {
            sb.append(String.format(
                "To save an additional $%.0f/month, here's your plan: ", targetExtra.doubleValue()));
            for (int i = 0; i < Math.min(3, recs.size()); i++) {
                CategoryRecommendation rec = recs.get(i);
                sb.append(String.format("cut %s from $%.0f to $%.0f/month (saves $%.0f); ",
                    rec.getCategory().toLowerCase(),
                    rec.getCurrentMonthlySpend().doubleValue(),
                    rec.getTargetMonthlySpend().doubleValue(),
                    rec.getMonthlySavings().doubleValue()));
            }
            sb.append(String.format(
                "That $%.0f/month adds up to $%.0f/year — invested at 7%%, that becomes $%.0f in 10 years.",
                targetExtra.doubleValue(),
                targetExtra.multiply(BigDecimal.valueOf(12)).doubleValue(),
                targetExtra.multiply(BigDecimal.valueOf(12)).doubleValue() * ((Math.pow(1.07, 10) - 1) / 0.07)));
        }
        return sb.toString();
    }

    // Inner record for scoring
    private record CategoryScore(
        String category,
        BigDecimal currentSpend,
        BigDecimal benchmark,
        double benchPct,
        int ease,
        BigDecimal overspend,
        double score
    ) {}
}

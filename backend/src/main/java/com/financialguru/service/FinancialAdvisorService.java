package com.financialguru.service;

import com.financialguru.model.Account;
import com.financialguru.model.FinancialProfile;
import com.financialguru.model.Subscription;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.SubscriptionRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialAdvisorService {

    private final OllamaService ollamaService;
    private final AccountRepository accountRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TransactionRepository transactionRepository;
    private final FinancialProfileService financialProfileService;

    public String chat(String userMessage) {
        String systemContext = buildSystemContext();
        String prompt = buildPrompt(systemContext, userMessage);
        return ollamaService.chat(prompt);
    }

    public List<String> getSuggestedQuestions() {
        return List.of(
            "How much did I spend last month?",
            "Which account has the highest utilization?",
            "What subscriptions am I paying for?",
            "Do I have any unusual charges?",
            "Which credit card should I pay off first?",
            "How much am I spending on dining and food?",
            "Are there any duplicate subscriptions?",
            "What are my upcoming payment due dates?",
            "How does my spending this month compare to last month?",
            "Which card has the best rewards for my spending pattern?"
        );
    }

    private String buildSystemContext() {
        List<Account> accounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        List<Subscription> subscriptions = subscriptionRepository.findByIsActiveTrueOrderByAnnualCostDesc();

        StringBuilder ctx = new StringBuilder();
        ctx.append("You are a personal financial advisor AI. You have access to the user's financial data. ");
        ctx.append("Be concise, practical, and focused. Provide specific actionable advice.\n\n");

        // Accounts summary
        ctx.append("ACCOUNTS:\n");
        for (Account a : accounts) {
            ctx.append(String.format("- %s (%s): Balance $%.2f",
                a.getName(), a.getType(),
                a.getCurrentBalance() != null ? a.getCurrentBalance() : BigDecimal.ZERO));
            if (a.getCreditLimit() != null) {
                ctx.append(String.format(", Limit $%.2f", a.getCreditLimit()));
            }
            if (a.getApr() != null) {
                ctx.append(String.format(", APR %.2f%%", a.getApr()));
            }
            if (a.getPromoApr() != null && a.getPromoAprEndDate() != null) {
                ctx.append(String.format(", Promo APR %.2f%% until %s",
                    a.getPromoApr(), a.getPromoAprEndDate()));
            }
            if (a.getPaymentDueDay() != null) {
                ctx.append(String.format(", Due day %d", a.getPaymentDueDay()));
            }
            ctx.append("\n");
        }

        // Subscriptions summary
        if (!subscriptions.isEmpty()) {
            ctx.append("\nACTIVE SUBSCRIPTIONS:\n");
            BigDecimal totalMonthly = BigDecimal.ZERO;
            for (Subscription s : subscriptions) {
                BigDecimal monthly = s.getFrequency() == Subscription.SubscriptionFrequency.MONTHLY
                    ? s.getAmount()
                    : s.getAnnualCost() != null ? s.getAnnualCost().divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
                totalMonthly = totalMonthly.add(monthly);
                ctx.append(String.format("- %s: $%.2f/%s (Annual: $%.2f)\n",
                    s.getMerchantName(), s.getAmount(), s.getFrequency(),
                    s.getAnnualCost() != null ? s.getAnnualCost() : BigDecimal.ZERO));
            }
            ctx.append(String.format("Total monthly subscriptions: $%.2f\n", totalMonthly));
        }

        ctx.append("\nToday's date: ").append(LocalDate.now()).append("\n");

        return ctx.toString();
    }

    private String buildPrompt(String systemContext, String userMessage) {
        return systemContext + "\n\nUser question: " + userMessage + "\n\nAnswer:";
    }

    /**
     * Enhanced chat that injects full financial context — spending, income, savings rate —
     * so the AI can answer wealth-building questions intelligently.
     */
    public String chatWithFullContext(String userMessage) {
        // Build rich context including spending breakdown
        String systemContext = buildEnrichedSystemContext();
        return ollamaService.chat(systemContext, userMessage);
    }

    private String buildEnrichedSystemContext() {
        LocalDate today = LocalDate.now();
        LocalDate threeMonthsAgo = today.minusMonths(3);

        List<Account> accounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        List<Subscription> subscriptions = subscriptionRepository.findByIsActiveTrueOrderByAnnualCostDesc();

        StringBuilder ctx = new StringBuilder();
        ctx.append("You are a highly experienced personal financial advisor with 15 years working with clients from all income levels. ");
        ctx.append("You have access to the user's complete financial data below. ");
        ctx.append("Be specific, use their actual numbers, give actionable advice. Never be vague or generic.\n\n");

        // Income
        BigDecimal income = BigDecimal.ZERO;
        try {
            FinancialProfile profile = financialProfileService.getOrCreateProfile();
            if (profile.getMonthlyIncome() != null && profile.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
                income = profile.getMonthlyIncome();
            }
        } catch (Exception ignored) {}
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal detected = transactionRepository.sumIncomeAmount(
                new BigDecimal("200"), threeMonthsAgo, today);
            if (detected != null) income = detected.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        }
        if (income.compareTo(BigDecimal.ZERO) > 0) {
            ctx.append(String.format("MONTHLY INCOME: $%.0f/month\n\n", income.doubleValue()));
        }

        // Spending by category
        List<Object[]> catTotals = transactionRepository.findAllCategoryTotals(threeMonthsAgo, today);
        if (!catTotals.isEmpty()) {
            ctx.append("SPENDING (3-month monthly average):\n");
            BigDecimal totalSpend = BigDecimal.ZERO;
            for (Object[] row : catTotals) {
                BigDecimal monthly = ((BigDecimal) row[1]).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
                totalSpend = totalSpend.add(monthly);
                ctx.append(String.format("- %s: $%.0f/month\n", row[0], monthly.doubleValue()));
            }
            ctx.append(String.format("TOTAL SPENDING: $%.0f/month\n", totalSpend.doubleValue()));
            if (income.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal savings = income.subtract(totalSpend);
                double savingsRate = savings.doubleValue() / income.doubleValue() * 100;
                ctx.append(String.format("SAVINGS: $%.0f/month (%.1f%% savings rate)\n\n", savings.doubleValue(), savingsRate));
            }
        }

        // Accounts
        ctx.append("ACCOUNTS:\n");
        for (Account a : accounts) {
            ctx.append(String.format("- %s (%s): Balance $%.2f",
                a.getName(), a.getType(),
                a.getCurrentBalance() != null ? a.getCurrentBalance() : BigDecimal.ZERO));
            if (a.getCreditLimit() != null)
                ctx.append(String.format(", Limit $%.2f", a.getCreditLimit()));
            if (a.getApr() != null)
                ctx.append(String.format(", APR %.2f%%", a.getApr()));
            ctx.append("\n");
        }

        // Subscriptions
        if (!subscriptions.isEmpty()) {
            BigDecimal totalMonthly = subscriptions.stream().map(s -> {
                if (s.getAmount() == null) return BigDecimal.ZERO;
                return switch (s.getFrequency()) {
                    case MONTHLY -> s.getAmount();
                    case QUARTERLY -> s.getAmount().divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
                    case ANNUAL -> s.getAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
                    case WEEKLY -> s.getAmount().multiply(BigDecimal.valueOf(4.33));
                };
            }).reduce(BigDecimal.ZERO, BigDecimal::add);
            ctx.append(String.format("\nSUBSCRIPTIONS: %d active, $%.0f/month total\n",
                subscriptions.size(), totalMonthly.doubleValue()));
            subscriptions.stream().limit(5).forEach(s ->
                ctx.append(String.format("- %s: $%.2f/%s\n", s.getMerchantName(), s.getAmount(), s.getFrequency())));
        }

        ctx.append("\nToday's date: ").append(today).append("\n\n");
        ctx.append("Answer the user's question with their SPECIFIC numbers. Be direct and actionable.\n");

        return ctx.toString();
    }
}

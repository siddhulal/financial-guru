package com.financialguru.service;

import com.financialguru.model.Account;
import com.financialguru.model.Subscription;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.SubscriptionRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
}

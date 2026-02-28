package com.financialguru.service;

import com.financialguru.dto.request.AlertRuleRequest;
import com.financialguru.model.Account;
import com.financialguru.model.Alert;
import com.financialguru.model.AlertRule;
import com.financialguru.model.Transaction;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.AlertRuleRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AlertService alertService;

    public List<AlertRule> getAllRules() {
        return alertRuleRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    public AlertRule createRule(AlertRuleRequest req) {
        return alertRuleRepository.save(AlertRule.builder()
                .name(req.getName())
                .ruleType(AlertRule.RuleType.valueOf(req.getRuleType()))
                .conditionOperator(req.getConditionOperator() != null
                        ? req.getConditionOperator() : "GREATER_THAN")
                .thresholdAmount(req.getThresholdAmount())
                .category(req.getCategory())
                .accountId(req.getAccountId())
                .isActive(true)
                .build());
    }

    public AlertRule updateRule(UUID id, AlertRuleRequest req) {
        AlertRule r = alertRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + id));
        if (req.getName() != null) r.setName(req.getName());
        if (req.getThresholdAmount() != null) r.setThresholdAmount(req.getThresholdAmount());
        if (req.getCategory() != null) r.setCategory(req.getCategory());
        return alertRuleRepository.save(r);
    }

    public void deleteRule(UUID id) {
        alertRuleRepository.deleteById(id);
    }

    public void evaluateRules() {
        List<AlertRule> rules = alertRuleRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        for (AlertRule rule : rules) {
            // Don't re-trigger if fired in last 24h
            if (rule.getLastTriggeredAt() != null
                    && rule.getLastTriggeredAt().isAfter(OffsetDateTime.now().minusHours(24))) {
                continue;
            }

            boolean triggered = false;
            String alertMessage = "";

            switch (rule.getRuleType()) {
                case TRANSACTION_AMOUNT -> {
                    // Check latest transactions for any exceeding threshold
                    List<Transaction> recent = transactionRepository.findRecentUnflaggedDebits(
                            today.minusDays(1));
                    for (Transaction t : recent) {
                        if (t.getAmount().compareTo(rule.getThresholdAmount()) > 0) {
                            triggered = true;
                            alertMessage = String.format(
                                    "Transaction of $%.2f at %s exceeded your $%.2f alert rule.",
                                    t.getAmount(), t.getMerchantName(), rule.getThresholdAmount());
                            break;
                        }
                    }
                }
                case MONTHLY_CATEGORY_SPEND -> {
                    if (rule.getCategory() != null) {
                        BigDecimal spent = transactionRepository.sumCategorySpending(
                                rule.getCategory(), monthStart, today);
                        if (spent != null && spent.compareTo(rule.getThresholdAmount()) > 0) {
                            triggered = true;
                            alertMessage = String.format(
                                    "%s spending this month ($%.2f) exceeded your $%.2f alert rule.",
                                    rule.getCategory(), spent, rule.getThresholdAmount());
                        }
                    }
                }
                case BALANCE_BELOW -> {
                    List<Account> accounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc();
                    for (Account a : accounts) {
                        if ((a.getType() == Account.AccountType.CHECKING
                                || a.getType() == Account.AccountType.SAVINGS)
                                && a.getCurrentBalance() != null
                                && a.getCurrentBalance().compareTo(rule.getThresholdAmount()) < 0) {
                            triggered = true;
                            alertMessage = String.format(
                                    "%s balance ($%.2f) is below your $%.2f alert threshold.",
                                    a.getName(), a.getCurrentBalance(), rule.getThresholdAmount());
                            break;
                        }
                    }
                }
                case UTILIZATION_ABOVE -> {
                    List<Account> cards = accountRepository.findByTypeOrderByNameAsc(
                            Account.AccountType.CREDIT_CARD);
                    BigDecimal totalBal = BigDecimal.ZERO;
                    BigDecimal totalLim = BigDecimal.ZERO;
                    for (Account c : cards) {
                        if (c.getCurrentBalance() != null) totalBal = totalBal.add(c.getCurrentBalance());
                        if (c.getCreditLimit() != null) totalLim = totalLim.add(c.getCreditLimit());
                    }
                    if (totalLim.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal util = totalBal.divide(totalLim, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                        if (util.compareTo(rule.getThresholdAmount()) > 0) {
                            triggered = true;
                            alertMessage = String.format(
                                    "Credit utilization (%.1f%%) exceeded your %.0f%% alert threshold.",
                                    util, rule.getThresholdAmount());
                        }
                    }
                }
            }

            if (triggered) {
                alertService.createAlert(Alert.AlertType.ANOMALY, Alert.AlertSeverity.MEDIUM,
                        "Custom Rule: " + rule.getName(), alertMessage, null, null, null);
                rule.setLastTriggeredAt(OffsetDateTime.now());
                alertRuleRepository.save(rule);
            }
        }
    }
}

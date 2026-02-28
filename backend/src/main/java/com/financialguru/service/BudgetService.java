package com.financialguru.service;

import com.financialguru.dto.response.BudgetStatusResponse;
import com.financialguru.model.Alert;
import com.financialguru.model.Budget;
import com.financialguru.repository.BudgetRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final AlertService alertService;

    public List<BudgetStatusResponse> getAllBudgetsWithStatus() {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        int daysPassed = today.getDayOfMonth();
        int daysInMonth = today.lengthOfMonth();

        List<Budget> budgets = budgetRepository.findByIsActiveTrueOrderByCategoryAsc();
        List<BudgetStatusResponse> result = new ArrayList<>();

        for (Budget b : budgets) {
            BigDecimal actual = transactionRepository.sumCategorySpending(b.getCategory(), startOfMonth, today);
            if (actual == null) actual = BigDecimal.ZERO;
            BigDecimal pct = b.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0
                ? actual.divide(b.getMonthlyLimit(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
            BigDecimal projected = daysPassed > 0
                ? actual.divide(BigDecimal.valueOf(daysPassed), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(daysInMonth))
                : BigDecimal.ZERO;
            String status = pct.compareTo(new BigDecimal("100")) >= 0 ? "RED"
                : pct.compareTo(new BigDecimal("80")) >= 0 ? "YELLOW" : "GREEN";

            result.add(BudgetStatusResponse.builder()
                .id(b.getId())
                .category(b.getCategory())
                .monthlyLimit(b.getMonthlyLimit())
                .actualSpend(actual)
                .percentUsed(pct)
                .status(status)
                .projectedMonthEnd(projected)
                .isActive(b.getIsActive())
                .build());
        }

        result.sort(Comparator.comparing(b -> {
            if ("RED".equals(b.getStatus())) return 0;
            if ("YELLOW".equals(b.getStatus())) return 1;
            return 2;
        }));
        return result;
    }

    public Budget upsertBudget(String category, BigDecimal limit) {
        Optional<Budget> existing = budgetRepository.findByCategory(category);
        if (existing.isPresent()) {
            Budget b = existing.get();
            b.setMonthlyLimit(limit);
            b.setIsActive(true);
            return budgetRepository.save(b);
        }
        return budgetRepository.save(Budget.builder()
            .category(category)
            .monthlyLimit(limit)
            .isActive(true)
            .build());
    }

    public void deleteBudget(UUID id) {
        budgetRepository.deleteById(id);
    }

    public void checkAndAlertBudgets() {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);

        for (Budget b : budgetRepository.findByIsActiveTrueOrderByCategoryAsc()) {
            BigDecimal actual = transactionRepository.sumCategorySpending(b.getCategory(), startOfMonth, today);
            if (actual == null) actual = BigDecimal.ZERO;
            BigDecimal pct = b.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0
                ? actual.divide(b.getMonthlyLimit(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

            if (pct.compareTo(new BigDecimal("100")) >= 0) {
                alertService.createAlert(Alert.AlertType.BUDGET_EXCEEDED, Alert.AlertSeverity.HIGH,
                    "Budget Exceeded: " + b.getCategory(),
                    String.format("%s budget exceeded: $%.2f spent of $%.2f limit (%.0f%%)",
                        b.getCategory(), actual, b.getMonthlyLimit(), pct),
                    null, null, null);
            } else if (pct.compareTo(new BigDecimal("80")) >= 0) {
                alertService.createAlert(Alert.AlertType.BUDGET_WARNING, Alert.AlertSeverity.MEDIUM,
                    "Budget Warning: " + b.getCategory(),
                    String.format("%s budget at %.0f%%: $%.2f spent of $%.2f limit",
                        b.getCategory(), pct, actual, b.getMonthlyLimit()),
                    null, null, null);
            }
        }
    }
}

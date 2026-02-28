package com.financialguru.controller;

import com.financialguru.dto.request.BudgetRequest;
import com.financialguru.dto.response.BudgetStatusResponse;
import com.financialguru.model.Budget;
import com.financialguru.repository.BudgetRepository;
import com.financialguru.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetRepository budgetRepository;

    @GetMapping
    public List<BudgetStatusResponse> getBudgets() {
        return budgetService.getAllBudgetsWithStatus();
    }

    @PostMapping
    public Budget createBudget(@RequestBody BudgetRequest req) {
        return budgetService.upsertBudget(req.getCategory(), req.getMonthlyLimit());
    }

    @PutMapping("/{id}")
    public Budget updateBudget(@PathVariable UUID id, @RequestBody BudgetRequest req) {
        return budgetService.upsertBudget(req.getCategory(), req.getMonthlyLimit());
    }

    @DeleteMapping("/{id}")
    public void deleteBudget(@PathVariable UUID id) {
        budgetService.deleteBudget(id);
    }
}

package com.financialguru.controller;

import com.financialguru.dto.request.SavingsGoalRequest;
import com.financialguru.dto.response.SavingsGoalResponse;
import com.financialguru.model.SavingsGoal;
import com.financialguru.service.SavingsGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GoalsController {

    private final SavingsGoalService savingsGoalService;

    @GetMapping
    public List<SavingsGoalResponse> getAllGoals() {
        return savingsGoalService.getAllGoals();
    }

    @PostMapping
    public SavingsGoal createGoal(@RequestBody SavingsGoalRequest req) {
        return savingsGoalService.createGoal(req);
    }

    @PutMapping("/{id}")
    public SavingsGoal updateGoal(@PathVariable UUID id, @RequestBody SavingsGoalRequest req) {
        return savingsGoalService.updateGoal(id, req);
    }

    @DeleteMapping("/{id}")
    public void deleteGoal(@PathVariable UUID id) {
        savingsGoalService.deleteGoal(id);
    }

    @PostMapping("/{id}/progress")
    public SavingsGoal addProgress(
            @PathVariable UUID id,
            @RequestBody Map<String, BigDecimal> body) {
        return savingsGoalService.addProgress(id, body.get("amount"));
    }
}

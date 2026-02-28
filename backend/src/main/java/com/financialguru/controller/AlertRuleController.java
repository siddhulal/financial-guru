package com.financialguru.controller;

import com.financialguru.dto.request.AlertRuleRequest;
import com.financialguru.model.AlertRule;
import com.financialguru.service.AlertRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    @GetMapping
    public List<AlertRule> getAllRules() {
        return alertRuleService.getAllRules();
    }

    @PostMapping
    public AlertRule createRule(@RequestBody AlertRuleRequest req) {
        return alertRuleService.createRule(req);
    }

    @PutMapping("/{id}")
    public AlertRule updateRule(@PathVariable UUID id, @RequestBody AlertRuleRequest req) {
        return alertRuleService.updateRule(id, req);
    }

    @DeleteMapping("/{id}")
    public void deleteRule(@PathVariable UUID id) {
        alertRuleService.deleteRule(id);
    }
}

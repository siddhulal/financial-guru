package com.financialguru.controller;

import com.financialguru.dto.request.FinancialProfileRequest;
import com.financialguru.model.FinancialProfile;
import com.financialguru.service.FinancialProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProfileController {

    private final FinancialProfileService financialProfileService;

    @GetMapping
    public FinancialProfile getProfile() {
        return financialProfileService.getOrCreateProfile();
    }

    @PutMapping
    public FinancialProfile updateProfile(@RequestBody FinancialProfileRequest req) {
        return financialProfileService.updateProfile(req);
    }

    @PostMapping("/detect-income")
    public Map<String, BigDecimal> detectIncome() {
        BigDecimal detected = financialProfileService.detectMonthlyIncome();
        return Map.of("monthlyIncome", detected);
    }
}

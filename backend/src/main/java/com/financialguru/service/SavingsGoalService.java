package com.financialguru.service;

import com.financialguru.dto.request.SavingsGoalRequest;
import com.financialguru.dto.response.SavingsGoalResponse;
import com.financialguru.model.FinancialProfile;
import com.financialguru.model.SavingsGoal;
import com.financialguru.repository.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsGoalService {

    private final SavingsGoalRepository savingsGoalRepository;
    private final FinancialProfileService financialProfileService;

    public List<SavingsGoalResponse> getAllGoals() {
        return savingsGoalRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private SavingsGoalResponse toResponse(SavingsGoal g) {
        BigDecimal remaining = g.getTargetAmount().subtract(g.getCurrentAmount()).max(BigDecimal.ZERO);
        BigDecimal pct = g.getTargetAmount().compareTo(BigDecimal.ZERO) > 0
                ? g.getCurrentAmount().divide(g.getTargetAmount(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        int monthsRemaining = 0;
        BigDecimal monthlyRequired = BigDecimal.ZERO;
        LocalDate projectedDate = null;
        boolean isOnTrack = false;

        if (g.getTargetDate() != null) {
            long months = ChronoUnit.MONTHS.between(LocalDate.now(), g.getTargetDate());
            monthsRemaining = (int) Math.max(months, 0);
            if (monthsRemaining > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
                monthlyRequired = remaining.divide(BigDecimal.valueOf(monthsRemaining), 2, RoundingMode.CEILING);
            }
            // Check if on track using profile monthly income
            FinancialProfile profile = financialProfileService.getOrCreateProfile();
            if (profile.getMonthlyIncome() != null && profile.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
                isOnTrack = monthlyRequired.compareTo(
                        profile.getMonthlyIncome().multiply(new BigDecimal("0.2"))) <= 0;
            }
        } else {
            // No target date — project based on a reasonable $500/month assumption
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal monthlyRate = BigDecimal.valueOf(500);
                long months = remaining.divide(monthlyRate, 0, RoundingMode.CEILING).longValue();
                projectedDate = LocalDate.now().plusMonths(months);
                monthsRemaining = (int) months;
            }
        }

        return SavingsGoalResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .category(g.getCategory())
                .targetAmount(g.getTargetAmount())
                .currentAmount(g.getCurrentAmount())
                .targetDate(g.getTargetDate())
                .linkedAccountId(g.getLinkedAccountId())
                .color(g.getColor())
                .isActive(g.getIsActive())
                .notes(g.getNotes())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt())
                .percentComplete(pct)
                .monthlyRequired(monthlyRequired)
                .projectedDate(projectedDate != null ? projectedDate : g.getTargetDate())
                .monthsRemaining(monthsRemaining)
                .isOnTrack(isOnTrack)
                .build();
    }

    public SavingsGoal createGoal(SavingsGoalRequest req) {
        return savingsGoalRepository.save(SavingsGoal.builder()
                .name(req.getName())
                .category(req.getCategory() != null ? req.getCategory() : "OTHER")
                .targetAmount(req.getTargetAmount())
                .currentAmount(req.getCurrentAmount() != null ? req.getCurrentAmount() : BigDecimal.ZERO)
                .targetDate(req.getTargetDate())
                .linkedAccountId(req.getLinkedAccountId())
                .color(req.getColor())
                .notes(req.getNotes())
                .isActive(true)
                .build());
    }

    @Transactional
    public SavingsGoal updateGoal(UUID id, SavingsGoalRequest req) {
        SavingsGoal g = savingsGoalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal not found: " + id));
        if (req.getName() != null) g.setName(req.getName());
        if (req.getTargetAmount() != null) g.setTargetAmount(req.getTargetAmount());
        if (req.getCurrentAmount() != null) g.setCurrentAmount(req.getCurrentAmount());
        if (req.getTargetDate() != null) g.setTargetDate(req.getTargetDate());
        if (req.getCategory() != null) g.setCategory(req.getCategory());
        if (req.getColor() != null) g.setColor(req.getColor());
        if (req.getNotes() != null) g.setNotes(req.getNotes());
        return savingsGoalRepository.save(g);
    }

    @Transactional
    public SavingsGoal addProgress(UUID id, BigDecimal amount) {
        SavingsGoal g = savingsGoalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal not found: " + id));
        g.setCurrentAmount(g.getCurrentAmount().add(amount));
        return savingsGoalRepository.save(g);
    }

    public void deleteGoal(UUID id) {
        savingsGoalRepository.deleteById(id);
    }
}

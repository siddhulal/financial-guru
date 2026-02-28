package com.financialguru.service;

import com.financialguru.dto.response.CreditScoreResponse;
import com.financialguru.model.Account;
import com.financialguru.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditScoreService {

    private final AccountRepository accountRepository;

    public CreditScoreResponse getOptimization() {
        List<Account> cards = accountRepository.findByTypeOrderByNameAsc(Account.AccountType.CREDIT_CARD);

        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal totalLimit = BigDecimal.ZERO;
        for (Account c : cards) {
            if (c.getCurrentBalance() != null) totalBalance = totalBalance.add(c.getCurrentBalance());
            if (c.getCreditLimit() != null) totalLimit = totalLimit.add(c.getCreditLimit());
        }
        BigDecimal overallUtil = totalLimit.compareTo(BigDecimal.ZERO) > 0
                ? totalBalance.divide(totalLimit, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Estimated FICO score based on utilization (simplified model)
        int estimatedScore = estimateScore(overallUtil);

        List<CreditScoreResponse.CardUtilizationDetail> cardDetails = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        List<CreditScoreResponse.WhatIfScenario> scenarios = new ArrayList<>();

        for (Account card : cards) {
            if (card.getCurrentBalance() == null || card.getCreditLimit() == null) continue;
            BigDecimal util = card.getCreditLimit().compareTo(BigDecimal.ZERO) > 0
                    ? card.getCurrentBalance().divide(card.getCreditLimit(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            // Recommended payment to reach 30% utilization
            BigDecimal target30 = card.getCreditLimit().multiply(new BigDecimal("0.30"));
            BigDecimal recommendedPayment = card.getCurrentBalance().subtract(target30).max(BigDecimal.ZERO);

            cardDetails.add(CreditScoreResponse.CardUtilizationDetail.builder()
                    .accountId(card.getId())
                    .accountName(card.getName())
                    .balance(card.getCurrentBalance())
                    .creditLimit(card.getCreditLimit())
                    .utilizationPct(util)
                    .recommendedPayment(recommendedPayment)
                    .targetUtilization(new BigDecimal("30"))
                    .build());

            if (util.compareTo(new BigDecimal("30")) > 0) {
                recommendations.add(String.format(
                        "Pay $%.2f on %s to reduce utilization from %.1f%% to 30%%",
                        recommendedPayment, card.getName(), util));
            }

            // What-if: pay to 30% utilization
            if (recommendedPayment.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal newTotalBalance = totalBalance.subtract(recommendedPayment);
                BigDecimal newUtil = totalLimit.compareTo(BigDecimal.ZERO) > 0
                        ? newTotalBalance.divide(totalLimit, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
                int newScore = estimateScore(newUtil);
                scenarios.add(CreditScoreResponse.WhatIfScenario.builder()
                        .description("Pay " + card.getName() + " to 30% utilization")
                        .paymentAmount(recommendedPayment)
                        .newUtilizationPct(newUtil)
                        .estimatedScoreImpact(newScore - estimatedScore)
                        .build());
            }
        }

        // Best strategy: pay highest utilization card first for score
        cards.stream()
                .filter(c -> c.getCurrentBalance() != null && c.getCreditLimit() != null
                        && c.getCreditLimit().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(c -> c.getCurrentBalance()
                        .divide(c.getCreditLimit(), 4, RoundingMode.HALF_UP)))
                .ifPresent(worst -> recommendations.add(0,
                        String.format("Focus first on %s (highest utilization card) for maximum credit score impact.",
                                worst.getName())));

        if (overallUtil.compareTo(new BigDecimal("30")) <= 0) {
            recommendations.add("Great job! Overall utilization is "
                    + overallUtil.setScale(1, RoundingMode.HALF_UP) + "% — below 30% is ideal.");
        }
        recommendations.add(
                "Pay your balance BEFORE the statement closing date to report a lower balance to credit bureaus.");

        return CreditScoreResponse.builder()
                .estimatedScore(estimatedScore)
                .utilizationImpact(overallUtil.compareTo(new BigDecimal("10")) <= 0 ? "EXCELLENT"
                        : overallUtil.compareTo(new BigDecimal("30")) <= 0 ? "GOOD"
                        : overallUtil.compareTo(new BigDecimal("50")) <= 0 ? "FAIR" : "POOR")
                .cards(cardDetails)
                .recommendations(recommendations)
                .whatIfScenarios(scenarios)
                .build();
    }

    private int estimateScore(BigDecimal utilPct) {
        // Simplified FICO estimate based on utilization component (30% of score)
        // Baseline score 650, max bonus 150 for utilization
        int base = 650;
        double pct = utilPct.doubleValue();
        int utilizationBonus = pct <= 1 ? 150 : pct <= 10 ? 130 : pct <= 30 ? 90
                : pct <= 50 ? 40 : pct <= 75 ? 10 : 0;
        return Math.min(850, base + utilizationBonus);
    }
}

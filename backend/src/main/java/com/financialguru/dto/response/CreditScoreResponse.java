package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditScoreResponse {
    private int estimatedScore;
    private String utilizationImpact;
    private List<CardUtilizationDetail> cards;
    private List<String> recommendations;
    private List<WhatIfScenario> whatIfScenarios;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardUtilizationDetail {
        private UUID accountId;
        private String accountName;
        private BigDecimal balance;
        private BigDecimal creditLimit;
        private BigDecimal utilizationPct;
        private BigDecimal recommendedPayment;
        private BigDecimal targetUtilization;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WhatIfScenario {
        private String description;
        private BigDecimal paymentAmount;
        private BigDecimal newUtilizationPct;
        private int estimatedScoreImpact;
    }
}

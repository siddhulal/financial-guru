package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthScoreResponse {

    private int totalScore;
    private String grade;
    private List<ScorePillar> pillars;
    private BigDecimal emergencyFundMonths;
    private int emergencyFundTarget;
    private BigDecimal utilizationPercent;
    private BigDecimal savingsRate;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScorePillar {
        private String name;
        private int score;
        private int maxScore;
        private String explanation;
    }
}

package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FireCalculatorResponse {
    private BigDecimal fiNumber;
    private BigDecimal currentSavings;
    private BigDecimal annualExpenses;
    private BigDecimal monthlySavings;
    private double yearsToFire;
    private LocalDate fireDate;
    private BigDecimal savingsRate;
    private BigDecimal monthlySavingsGap;
    private List<YearProjection> projections;
    private List<BigDecimal> monteCarloP10;
    private List<BigDecimal> monteCarloP50;
    private List<BigDecimal> monteCarloP90;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YearProjection {
        private int year;
        private BigDecimal portfolioValue;
        private BigDecimal annualContribution;
    }
}

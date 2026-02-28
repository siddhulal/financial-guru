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
public class MerchantTrendResponse {
    private String merchantName;
    private List<MonthlyAmount> months;
    private BigDecimal totalAnnual;
    private BigDecimal avgMonthly;
    private String trend; // INCREASING, STABLE, DECREASING

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyAmount {
        private String month;
        private BigDecimal amount;
    }
}

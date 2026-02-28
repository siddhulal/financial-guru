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
public class SpendingHeatmapResponse {
    private int year;
    private List<HeatmapDay> days;
    private BigDecimal maxDailySpend;
    private BigDecimal totalAnnualSpend;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeatmapDay {
        private LocalDate date;
        private BigDecimal totalSpend;
        private int transactionCount;
        private int intensity; // 0 to 4
    }
}

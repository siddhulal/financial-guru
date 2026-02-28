package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowResponse {

    private int year;
    private int month;
    private BigDecimal startingBalance;
    private List<CashFlowEvent> events;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashFlowEvent {
        private LocalDate date;
        private String type;
        private String description;
        private BigDecimal amount;
        @Setter
        private BigDecimal runningBalance;
        @Setter
        private Boolean isDangerDay;
    }
}

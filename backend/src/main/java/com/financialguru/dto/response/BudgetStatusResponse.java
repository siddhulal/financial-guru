package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetStatusResponse {

    private UUID id;
    private String category;
    private BigDecimal monthlyLimit;
    private BigDecimal actualSpend;
    private BigDecimal percentUsed;
    private String status;
    private BigDecimal projectedMonthEnd;
    private Boolean isActive;
}

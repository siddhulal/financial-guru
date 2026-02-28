package com.financialguru.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancialProfileRequest {

    private BigDecimal monthlyIncome;
    private String incomeSource;
    private String payFrequency;
    private Integer emergencyFundTargetMonths;
    private String notes;
}

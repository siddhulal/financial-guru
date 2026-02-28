package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatIfDataPoint {

    private BigDecimal extraPayment;
    private int avalancheMonths;
    private BigDecimal avalancheTotalInterest;
    private LocalDate avalanchePayoffDate;
    private int snowballMonths;
}

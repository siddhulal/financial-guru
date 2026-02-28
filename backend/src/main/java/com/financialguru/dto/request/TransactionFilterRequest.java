package com.financialguru.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class TransactionFilterRequest {
    private UUID accountId;
    private String category;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String search;
    private int page = 0;
    private int size = 50;
}

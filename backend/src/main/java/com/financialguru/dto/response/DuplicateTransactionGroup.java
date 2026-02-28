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
public class DuplicateTransactionGroup {
    private String merchantName;
    private BigDecimal amount;
    private List<TransactionResponse> transactions;
    private int withinDays;
}

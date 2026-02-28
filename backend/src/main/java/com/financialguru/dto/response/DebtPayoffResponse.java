package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtPayoffResponse {

    private BigDecimal extraPayment;
    private BigDecimal totalCurrentDebt;
    private PayoffStrategy avalanche;
    private PayoffStrategy snowball;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayoffStrategy {
        private String strategy;
        private int totalMonths;
        private LocalDate payoffDate;
        private BigDecimal totalInterest;
        private BigDecimal totalPaid;
        private List<CardPayoffDetail> cardOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardPayoffDetail {
        private UUID accountId;
        private String accountName;
        private BigDecimal currentBalance;
        private BigDecimal apr;
        private BigDecimal minPayment;
        private LocalDate payoffDate;
        private BigDecimal interestPaid;
        private int payoffOrder;
    }
}

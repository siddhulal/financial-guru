package com.financialguru.dto.request;

import com.financialguru.model.Account;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AccountRequest {

    @NotBlank(message = "Account name is required")
    private String name;

    private String institution;

    @NotNull(message = "Account type is required")
    private Account.AccountType type;

    private String last4;
    private BigDecimal creditLimit;
    private BigDecimal currentBalance;
    private BigDecimal availableCredit;
    private BigDecimal apr;
    private BigDecimal promoApr;
    private LocalDate promoAprEndDate;
    private Integer paymentDueDay;
    private BigDecimal minPayment;
    private String rewardsProgram;
    private String color;
}

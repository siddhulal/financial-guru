package com.financialguru.dto.response;

import com.financialguru.model.Account;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class AccountResponse {
    private UUID id;
    private String name;
    private String institution;
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
    private Boolean isActive;
    private BigDecimal utilizationPercent;
    private Integer daysUntilPromoAprExpiry;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static AccountResponse from(Account account) {
        BigDecimal availableCredit = account.getAvailableCredit();
        if (availableCredit == null
                && account.getCreditLimit() != null
                && account.getCurrentBalance() != null) {
            availableCredit = account.getCreditLimit().subtract(account.getCurrentBalance());
        }

        BigDecimal utilization = null;
        if (account.getCreditLimit() != null && account.getCreditLimit().compareTo(BigDecimal.ZERO) > 0
            && account.getCurrentBalance() != null) {
            utilization = account.getCurrentBalance()
                .divide(account.getCreditLimit(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }

        Integer daysUntilExpiry = null;
        if (account.getPromoAprEndDate() != null) {
            daysUntilExpiry = (int) java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.now(), account.getPromoAprEndDate());
        }

        return AccountResponse.builder()
            .id(account.getId())
            .name(account.getName())
            .institution(account.getInstitution())
            .type(account.getType())
            .last4(account.getLast4())
            .creditLimit(account.getCreditLimit())
            .currentBalance(account.getCurrentBalance())
            .availableCredit(availableCredit)
            .apr(account.getApr())
            .promoApr(account.getPromoApr())
            .promoAprEndDate(account.getPromoAprEndDate())
            .paymentDueDay(account.getPaymentDueDay())
            .minPayment(account.getMinPayment())
            .rewardsProgram(account.getRewardsProgram())
            .color(account.getColor())
            .isActive(account.getIsActive())
            .utilizationPercent(utilization)
            .daysUntilPromoAprExpiry(daysUntilExpiry)
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .build();
    }
}

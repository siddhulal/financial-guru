package com.financialguru.dto.response;

import com.financialguru.model.Transaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private UUID accountId;
    private String accountName;
    private UUID statementId;
    private LocalDate transactionDate;
    private LocalDate postDate;
    private String description;
    private String merchantName;
    private String category;
    private String subcategory;
    private BigDecimal amount;
    private Transaction.TransactionType type;
    private String referenceNumber;
    private Boolean isRecurring;
    private Boolean isFlagged;
    private String flagReason;
    private String notes;
    private OffsetDateTime createdAt;

    public static TransactionResponse from(Transaction t) {
        return TransactionResponse.builder()
            .id(t.getId())
            .accountId(t.getAccount() != null ? t.getAccount().getId() : null)
            .accountName(t.getAccount() != null ? t.getAccount().getName() : null)
            .statementId(t.getStatement() != null ? t.getStatement().getId() : null)
            .transactionDate(t.getTransactionDate())
            .postDate(t.getPostDate())
            .description(t.getDescription())
            .merchantName(t.getMerchantName())
            .category(t.getCategory())
            .subcategory(t.getSubcategory())
            .amount(t.getAmount())
            .type(t.getType())
            .referenceNumber(t.getReferenceNumber())
            .isRecurring(t.getIsRecurring())
            .isFlagged(t.getIsFlagged())
            .flagReason(t.getFlagReason())
            .notes(t.getNotes())
            .createdAt(t.getCreatedAt())
            .build();
    }
}

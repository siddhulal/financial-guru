package com.financialguru.dto.response;

import com.financialguru.model.Alert;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class AlertResponse {
    private UUID id;
    private Alert.AlertType type;
    private Alert.AlertSeverity severity;
    private String title;
    private String message;
    private String aiExplanation;
    private UUID accountId;
    private String accountName;
    private UUID transactionId;
    private Boolean isRead;
    private Boolean isResolved;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime createdAt;

    public static AlertResponse from(Alert a) {
        return AlertResponse.builder()
            .id(a.getId())
            .type(a.getType())
            .severity(a.getSeverity())
            .title(a.getTitle())
            .message(a.getMessage())
            .aiExplanation(a.getAiExplanation())
            .accountId(a.getAccount() != null ? a.getAccount().getId() : null)
            .accountName(a.getAccount() != null ? a.getAccount().getName() : null)
            .transactionId(a.getTransaction() != null ? a.getTransaction().getId() : null)
            .isRead(a.getIsRead())
            .isResolved(a.getIsResolved())
            .resolvedAt(a.getResolvedAt())
            .createdAt(a.getCreatedAt())
            .build();
    }
}

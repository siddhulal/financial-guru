package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingsGoalResponse {
    private UUID id;
    private String name;
    private String category;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private LocalDate targetDate;
    private UUID linkedAccountId;
    private String color;
    private Boolean isActive;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Computed fields
    private BigDecimal percentComplete;
    private BigDecimal monthlyRequired;
    private LocalDate projectedDate;
    private int monthsRemaining;
    private Boolean isOnTrack;
}

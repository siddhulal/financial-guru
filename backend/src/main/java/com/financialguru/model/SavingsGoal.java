package com.financialguru.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "savings_goals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private String category = "OTHER";

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal targetAmount;

    @Builder.Default
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal currentAmount = BigDecimal.ZERO;

    private LocalDate targetDate;

    @Column(name = "linked_account_id")
    private UUID linkedAccountId;

    private String color;

    @Builder.Default
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;

    public enum GoalCategory {
        EMERGENCY_FUND,
        VACATION,
        DOWN_PAYMENT,
        CAR,
        RETIREMENT,
        EDUCATION,
        OTHER
    }
}

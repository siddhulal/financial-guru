package com.financialguru.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RuleType ruleType;

    @Builder.Default
    @Column(nullable = false)
    private String conditionOperator = "GREATER_THAN";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal thresholdAmount;

    private String category;

    @Column(name = "account_id")
    private UUID accountId;

    @Builder.Default
    private Boolean isActive = true;

    private OffsetDateTime lastTriggeredAt;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;

    public enum RuleType {
        TRANSACTION_AMOUNT,
        MONTHLY_CATEGORY_SPEND,
        BALANCE_BELOW,
        UTILIZATION_ABOVE
    }
}

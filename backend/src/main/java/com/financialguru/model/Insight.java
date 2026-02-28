package com.financialguru.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "insights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Insight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InsightType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "action_text", length = 500)
    private String actionText;

    @Column(name = "impact_amount", precision = 12, scale = 2)
    private BigDecimal impactAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InsightSeverity severity;

    @Column(name = "merchant_name", length = 255)
    private String merchantName;

    @Column(length = 100)
    private String category;

    @Column(name = "is_dismissed")
    @Builder.Default
    private Boolean isDismissed = false;

    @CreationTimestamp
    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    public enum InsightType {
        PRICE_INCREASE, DUPLICATE_CROSS_CARD, SUBSCRIPTION_CREEP,
        ATM_FEE_WASTE, REWARDS_OPPORTUNITY, CATEGORY_YOY_SPIKE, BILL_INCREASE,
        SPENDING_YOUR_RAISE
    }

    public enum InsightSeverity {
        INFO, WARNING, OPPORTUNITY, CRITICAL
    }
}

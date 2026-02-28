package com.financialguru.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "net_worth_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetWorthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "liquid_assets", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal liquidAssets = BigDecimal.ZERO;

    @Column(name = "credit_card_debt", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal creditCardDebt = BigDecimal.ZERO;

    @Column(name = "manual_assets", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal manualAssets = BigDecimal.ZERO;

    @Column(name = "manual_liabilities", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal manualLiabilities = BigDecimal.ZERO;

    @Column(name = "net_worth", nullable = false, precision = 14, scale = 2)
    private BigDecimal netWorth;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}

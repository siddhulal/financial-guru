package com.financialguru.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String institution;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountType type;

    private String last4;

    @Column(precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(precision = 12, scale = 2)
    private BigDecimal currentBalance;

    @Column(precision = 12, scale = 2)
    private BigDecimal availableCredit;

    @Column(precision = 5, scale = 2)
    private BigDecimal apr;

    @Column(precision = 5, scale = 2)
    private BigDecimal promoApr;

    private LocalDate promoAprEndDate;

    private Integer paymentDueDay;

    @Column(precision = 12, scale = 2)
    private BigDecimal minPayment;

    private String rewardsProgram;

    private String color;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;

    public enum AccountType {
        CHECKING, SAVINGS, CREDIT_CARD, LOAN
    }
}

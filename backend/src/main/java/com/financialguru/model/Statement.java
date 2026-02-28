package com.financialguru.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "statements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String filePath;

    private LocalDate statementMonth;
    private LocalDate startDate;
    private LocalDate endDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal openingBalance;

    @Column(precision = 12, scale = 2)
    private BigDecimal closingBalance;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalCredits;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalDebits;

    /** Minimum payment due amount from statement header */
    @Column(precision = 12, scale = 2)
    private BigDecimal minimumPayment;

    /** Exact payment due date from statement header */
    private LocalDate paymentDueDate;

    /** "Total fees charged in YYYY" from YTD summary section */
    @Column(precision = 12, scale = 2)
    private BigDecimal ytdTotalFees;

    /** "Total interest charged in YYYY" from YTD summary section */
    @Column(precision = 12, scale = 2)
    private BigDecimal ytdTotalInterest;

    /** The calendar year these YTD totals apply to */
    private Integer ytdYear;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatementStatus status = StatementStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    public enum StatementStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}

package com.financialguru.repository;

import com.financialguru.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Page<Transaction> findByAccountIdOrderByTransactionDateDesc(UUID accountId, Pageable pageable);

    List<Transaction> findByAccountId(UUID accountId);

    List<Transaction> findByStatementIdOrderByTransactionDateDesc(UUID statementId);

    List<Transaction> findByIsFlaggedTrueOrderByTransactionDateDesc();

    // findWithFilters removed — replaced by JpaSpecificationExecutor in TransactionService

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.account.id = :accountId
          AND t.merchantName = :merchantName
          AND t.transactionDate >= :since
        ORDER BY t.transactionDate DESC
        """)
    List<Transaction> findByAccountAndMerchantSince(
        @Param("accountId") UUID accountId,
        @Param("merchantName") String merchantName,
        @Param("since") LocalDate since
    );

    @Query("""
        SELECT t.category, SUM(t.amount) as total
        FROM Transaction t
        WHERE t.account.id = :accountId
          AND t.transactionDate >= :startDate
          AND t.transactionDate <= :endDate
          AND t.type = 'DEBIT'
          AND t.category IS NOT NULL
        GROUP BY t.category
        ORDER BY total DESC
        """)
    List<Object[]> findCategoryTotals(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM'), SUM(t.amount)
        FROM Transaction t
        WHERE t.account.id = :accountId
          AND t.type = 'DEBIT'
          AND t.transactionDate >= :since
        GROUP BY FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM')
        ORDER BY FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM')
        """)
    List<Object[]> findMonthlySpendingTrend(
        @Param("accountId") UUID accountId,
        @Param("since") LocalDate since
    );

    // ── Global queries (include transactions not linked to any account) ──────

    @Query("""
        SELECT SUM(t.amount) FROM Transaction t
        WHERE t.type = 'DEBIT'
          AND t.transactionDate >= :start
          AND t.transactionDate <= :end
        """)
    BigDecimal sumAllSpending(
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    @Query("""
        SELECT FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM'), SUM(t.amount)
        FROM Transaction t
        WHERE t.type = 'DEBIT'
          AND t.transactionDate >= :since
        GROUP BY FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM')
        ORDER BY FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM')
        """)
    List<Object[]> findAllMonthlySpendingTrend(@Param("since") LocalDate since);

    @Query("""
        SELECT t.category, SUM(t.amount) as total
        FROM Transaction t
        WHERE t.type = 'DEBIT'
          AND t.category IS NOT NULL
          AND t.transactionDate >= :start
          AND t.transactionDate <= :end
        GROUP BY t.category
        ORDER BY total DESC
        """)
    List<Object[]> findAllCategoryTotals(
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    @Query("""
        SELECT t.merchantName, SUM(t.amount), COUNT(t)
        FROM Transaction t
        WHERE t.type = 'DEBIT'
          AND t.merchantName IS NOT NULL
          AND t.transactionDate >= :start
          AND t.transactionDate <= :end
        GROUP BY t.merchantName
        ORDER BY SUM(t.amount) DESC
        """)
    List<Object[]> findAllTopMerchants(
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    List<Transaction> findByStatementId(UUID statementId);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.type = 'CREDIT'
          AND t.amount >= :minAmount
          AND t.transactionDate >= :since
          AND t.account.type = 'CHECKING'
        ORDER BY t.transactionDate DESC
        """)
    List<Transaction> findPotentialIncomeTransactions(
        @Param("minAmount") BigDecimal minAmount,
        @Param("since") LocalDate since
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.category = :category
          AND t.type = 'DEBIT'
          AND t.transactionDate >= :start
          AND t.transactionDate <= :end
        """)
    BigDecimal sumCategorySpending(
        @Param("category") String category,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.merchantName = :merchant
          AND t.type = 'DEBIT'
          AND t.transactionDate >= :start
          AND t.transactionDate <= :end
        """)
    BigDecimal sumMerchantSpending(
        @Param("merchant") String merchant,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.merchantName = :merchant
          AND t.type = 'DEBIT'
          AND t.transactionDate >= :start
          AND t.transactionDate <= :end
        ORDER BY t.transactionDate DESC
        """)
    List<Transaction> findByMerchantAndDateRange(
        @Param("merchant") String merchant,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    @Query("""
        SELECT t FROM Transaction t
        WHERE (t.type = 'FEE' OR UPPER(t.description) LIKE '%ATM%')
          AND t.transactionDate >= :since
        ORDER BY t.transactionDate DESC
        """)
    List<Transaction> findAllFeeTransactions(@Param("since") LocalDate since);

    // Daily spending for heatmap
    @Query("""
        SELECT t.transactionDate, SUM(t.amount), COUNT(t)
        FROM Transaction t
        WHERE t.type = 'DEBIT'
          AND t.transactionDate >= :start
          AND t.transactionDate <= :end
        GROUP BY t.transactionDate
        ORDER BY t.transactionDate
        """)
    List<Object[]> findDailySpendingTotals(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // Merchant monthly trend (12 months)
    @Query("""
        SELECT FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM'), SUM(t.amount)
        FROM Transaction t
        WHERE t.merchantName = :merchant
          AND t.type = 'DEBIT'
          AND t.transactionDate >= :since
        GROUP BY FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM')
        ORDER BY FUNCTION('TO_CHAR', t.transactionDate, 'YYYY-MM')
        """)
    List<Object[]> findMerchantMonthlyTrend(@Param("merchant") String merchant, @Param("since") LocalDate since);

    // Potential duplicates: same amount+merchant on different accounts within N days
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.type = 'DEBIT'
          AND t.transactionDate >= :since
          AND t.isFlagged = false
        ORDER BY t.merchantName, t.amount, t.transactionDate DESC
        """)
    List<Transaction> findRecentUnflaggedDebits(@Param("since") LocalDate since);

    // Bulk category update helper - find by ids
    @Query("SELECT t FROM Transaction t WHERE t.id IN :ids")
    List<Transaction> findAllByIds(@Param("ids") List<UUID> ids);

    // Search transactions
    @Query("""
        SELECT t FROM Transaction t
        WHERE (LOWER(t.merchantName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(t.category) LIKE LOWER(CONCAT('%', :query, '%')))
          AND t.type = 'DEBIT'
        ORDER BY t.transactionDate DESC
        """)
    List<Transaction> searchTransactions(@Param("query") String query, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.category IN :categories
          AND t.type = 'DEBIT'
          AND t.transactionDate BETWEEN :start AND :end
        """)
    BigDecimal sumCategoriesSpending(
        @Param("categories") List<String> categories,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.type IN ('CREDIT', 'PAYMENT')
          AND t.amount >= :minAmount
          AND t.transactionDate BETWEEN :start AND :end
        """)
    BigDecimal sumIncomeAmount(
        @Param("minAmount") BigDecimal minAmount,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end);
}

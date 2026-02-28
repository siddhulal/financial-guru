package com.financialguru.repository;

import com.financialguru.model.Statement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StatementRepository extends JpaRepository<Statement, UUID> {

    List<Statement> findByAccountIdOrderByStatementMonthDesc(UUID accountId);

    List<Statement> findByStatusOrderByCreatedAtDesc(Statement.StatementStatus status);

    @Query("SELECT s FROM Statement s ORDER BY s.createdAt DESC")
    List<Statement> findAllOrderByCreatedAtDesc();

    @Query("SELECT s FROM Statement s WHERE s.account.id = :accountId AND s.status = 'COMPLETED' ORDER BY s.statementMonth DESC")
    List<Statement> findCompletedByAccountId(@Param("accountId") UUID accountId);
}

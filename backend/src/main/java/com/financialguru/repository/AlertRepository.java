package com.financialguru.repository;

import com.financialguru.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findByIsResolvedFalseOrderByCreatedAtDesc();

    List<Alert> findByIsReadFalseAndIsResolvedFalseOrderByCreatedAtDesc();

    List<Alert> findBySeverityAndIsResolvedFalseOrderByCreatedAtDesc(Alert.AlertSeverity severity);

    List<Alert> findByAccountIdAndIsResolvedFalseOrderByCreatedAtDesc(UUID accountId);

    long countByIsReadFalseAndIsResolvedFalse();

    @Query("SELECT a FROM Alert a WHERE a.isResolved = false ORDER BY a.createdAt DESC LIMIT 10")
    List<Alert> findRecentUnresolved();
}

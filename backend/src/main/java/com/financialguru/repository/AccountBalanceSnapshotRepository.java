package com.financialguru.repository;

import com.financialguru.model.AccountBalanceSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountBalanceSnapshotRepository extends JpaRepository<AccountBalanceSnapshot, UUID> {

    List<AccountBalanceSnapshot> findByAccountIdAndSnapshotDateAfterOrderBySnapshotDateAsc(
            UUID accountId, LocalDate since);

    Optional<AccountBalanceSnapshot> findByAccountIdAndSnapshotDate(UUID accountId, LocalDate date);

    @Query("SELECT s FROM AccountBalanceSnapshot s WHERE s.account.id = :accountId ORDER BY s.snapshotDate DESC")
    List<AccountBalanceSnapshot> findRecentByAccountId(@Param("accountId") UUID accountId, Pageable pageable);
}

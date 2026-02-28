package com.financialguru.repository;

import com.financialguru.model.NetWorthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshot, UUID> {

    List<NetWorthSnapshot> findTop12ByOrderBySnapshotDateDesc();

    Optional<NetWorthSnapshot> findBySnapshotDate(LocalDate date);

    @Query("SELECT s FROM NetWorthSnapshot s WHERE s.snapshotDate >= :start ORDER BY s.snapshotDate ASC")
    List<NetWorthSnapshot> findSince(@Param("start") LocalDate start);
}

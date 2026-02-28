package com.financialguru.repository;

import com.financialguru.model.Insight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InsightRepository extends JpaRepository<Insight, UUID> {

    List<Insight> findByIsDismissedFalseOrderByGeneratedAtDesc();

    @Query("SELECT i FROM Insight i WHERE i.type = :type AND (:merchant IS NULL OR i.merchantName = :merchant) AND i.generatedAt >= :since")
    List<Insight> findRecentByTypeAndMerchant(
        @Param("type") Insight.InsightType type,
        @Param("merchant") String merchant,
        @Param("since") OffsetDateTime since
    );

    @Modifying
    @Query("DELETE FROM Insight i WHERE i.generatedAt < :cutoff")
    void deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}

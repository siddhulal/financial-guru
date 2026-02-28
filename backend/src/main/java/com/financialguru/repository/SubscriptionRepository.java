package com.financialguru.repository;

import com.financialguru.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByIsActiveTrueOrderByAnnualCostDesc();

    List<Subscription> findByIsDuplicateTrueAndIsActiveTrue();

    List<Subscription> findByAccountIdAndIsActiveTrue(UUID accountId);

    Optional<Subscription> findByNormalizedNameAndAccountId(String normalizedName, UUID accountId);

    @Query("""
        SELECT s FROM Subscription s
        WHERE s.isActive = true
          AND s.normalizedName IN (
            SELECT s2.normalizedName FROM Subscription s2
            GROUP BY s2.normalizedName
            HAVING COUNT(DISTINCT s2.account.id) > 1
          )
        ORDER BY s.normalizedName, s.account.id
        """)
    List<Subscription> findPotentialDuplicates();
}

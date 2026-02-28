package com.financialguru.repository;

import com.financialguru.model.FinancialProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialProfileRepository extends JpaRepository<FinancialProfile, UUID> {

    @Query("SELECT p FROM FinancialProfile p ORDER BY p.createdAt ASC")
    Optional<FinancialProfile> findFirst();
}

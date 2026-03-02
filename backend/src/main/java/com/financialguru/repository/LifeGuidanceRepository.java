package com.financialguru.repository;

import com.financialguru.model.LifeGuidance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LifeGuidanceRepository extends JpaRepository<LifeGuidance, UUID> {

    List<LifeGuidance> findByIsDismissedFalseOrderByGeneratedAtDesc();

    List<LifeGuidance> findByGuidanceTypeOrderByGeneratedAtDesc(String guidanceType, Pageable pageable);

    List<LifeGuidance> findByGuidanceTypeAndIsDismissedFalseOrderByGeneratedAtDesc(String guidanceType);
}

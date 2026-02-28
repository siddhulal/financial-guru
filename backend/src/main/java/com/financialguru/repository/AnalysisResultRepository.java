package com.financialguru.repository;

import com.financialguru.model.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {

    List<AnalysisResult> findByStatementIdOrderByCreatedAtDesc(UUID statementId);

    List<AnalysisResult> findByStatementIdAndAnalysisTypeOrderByCreatedAtDesc(
        UUID statementId,
        AnalysisResult.AnalysisType analysisType
    );
}

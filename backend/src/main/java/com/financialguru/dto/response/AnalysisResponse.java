package com.financialguru.dto.response;

import com.financialguru.model.AnalysisResult;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class AnalysisResponse {
    private UUID id;
    private UUID statementId;
    private AnalysisResult.AnalysisType analysisType;
    private Map<String, Object> resultData;
    private String modelUsed;
    private Integer processingMs;
    private OffsetDateTime createdAt;

    public static AnalysisResponse from(AnalysisResult r) {
        return AnalysisResponse.builder()
            .id(r.getId())
            .statementId(r.getStatement() != null ? r.getStatement().getId() : null)
            .analysisType(r.getAnalysisType())
            .resultData(r.getResultData())
            .modelUsed(r.getModelUsed())
            .processingMs(r.getProcessingMs())
            .createdAt(r.getCreatedAt())
            .build();
    }
}

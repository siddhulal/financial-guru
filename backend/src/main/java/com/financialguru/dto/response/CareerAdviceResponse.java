package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerAdviceResponse {

    private String jobTitle;
    private String industry;
    private String location;
    private String percentileLabel;

    private BigDecimal currentSalary;
    private BigDecimal marketP25;
    private BigDecimal marketP50;
    private BigDecimal marketP75;
    private BigDecimal salaryGap;
    private boolean isUnderpaid;

    // RECOMMENDED | NEUTRAL | NOT_NOW
    private String jobChangeRecommendation;
    private String jobChangeReasoning;

    private List<String> skillsToLearn;
    private String careerPathAdvice;
    private String aiNarrative;

    // GEMINI | OLLAMA
    private String source;

    private boolean profileComplete;
    private String profileMessage;
}

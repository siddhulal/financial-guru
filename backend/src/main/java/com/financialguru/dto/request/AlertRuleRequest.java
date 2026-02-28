package com.financialguru.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleRequest {
    private String name;
    private String ruleType;
    private String conditionOperator;
    private BigDecimal thresholdAmount;
    private String category;
    private UUID accountId;
}

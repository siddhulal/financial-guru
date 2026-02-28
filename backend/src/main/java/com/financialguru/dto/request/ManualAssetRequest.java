package com.financialguru.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualAssetRequest {

    private String name;
    private String assetType;
    private String assetClass;
    private BigDecimal currentValue;
    private String notes;
}

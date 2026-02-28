package com.financialguru.dto.response;

import com.financialguru.model.ManualAsset;
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
public class NetWorthResponse {

    private BigDecimal netWorth;
    private BigDecimal liquidAssets;
    private BigDecimal creditCardDebt;
    private BigDecimal manualAssetsTotal;
    private BigDecimal manualLiabilities;
    private BigDecimal monthlyChange;
    private BigDecimal yearlyChange;
    private List<ManualAsset> assets;
}

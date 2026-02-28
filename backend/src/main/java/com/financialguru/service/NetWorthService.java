package com.financialguru.service;

import com.financialguru.dto.request.ManualAssetRequest;
import com.financialguru.dto.response.NetWorthResponse;
import com.financialguru.model.Account;
import com.financialguru.model.ManualAsset;
import com.financialguru.model.NetWorthSnapshot;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.ManualAssetRepository;
import com.financialguru.repository.NetWorthSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NetWorthService {

    private final NetWorthSnapshotRepository netWorthSnapshotRepository;
    private final ManualAssetRepository manualAssetRepository;
    private final AccountRepository accountRepository;

    public NetWorthResponse getCurrentNetWorth() {
        List<Account> accounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        BigDecimal liquidAssets = BigDecimal.ZERO;
        BigDecimal ccDebt = BigDecimal.ZERO;
        for (Account a : accounts) {
            if (a.getCurrentBalance() == null) continue;
            switch (a.getType()) {
                case CHECKING, SAVINGS -> liquidAssets = liquidAssets.add(a.getCurrentBalance());
                case CREDIT_CARD -> ccDebt = ccDebt.add(a.getCurrentBalance());
                default -> {}
            }
        }

        List<ManualAsset> assets = manualAssetRepository.findAll();
        BigDecimal manualAssetsTotal = assets.stream()
            .filter(a -> a.getAssetType() == ManualAsset.AssetType.ASSET)
            .map(ManualAsset::getCurrentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal manualLiabilities = assets.stream()
            .filter(a -> a.getAssetType() == ManualAsset.AssetType.LIABILITY)
            .map(ManualAsset::getCurrentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netWorth = liquidAssets.subtract(ccDebt).add(manualAssetsTotal).subtract(manualLiabilities);

        List<NetWorthSnapshot> history = netWorthSnapshotRepository.findTop12ByOrderBySnapshotDateDesc();
        BigDecimal monthlyChange = BigDecimal.ZERO;
        BigDecimal yearlyChange = BigDecimal.ZERO;
        if (!history.isEmpty()) {
            monthlyChange = netWorth.subtract(history.get(0).getNetWorth());
            if (history.size() >= 12) {
                yearlyChange = netWorth.subtract(history.get(history.size() - 1).getNetWorth());
            }
        }

        return NetWorthResponse.builder()
            .netWorth(netWorth)
            .liquidAssets(liquidAssets)
            .creditCardDebt(ccDebt)
            .manualAssetsTotal(manualAssetsTotal)
            .manualLiabilities(manualLiabilities)
            .monthlyChange(monthlyChange)
            .yearlyChange(yearlyChange)
            .assets(assets)
            .build();
    }

    public List<NetWorthSnapshot> getHistory() {
        return netWorthSnapshotRepository.findTop12ByOrderBySnapshotDateDesc();
    }

    public NetWorthSnapshot captureSnapshot() {
        NetWorthResponse current = getCurrentNetWorth();
        LocalDate today = LocalDate.now();
        NetWorthSnapshot snapshot = NetWorthSnapshot.builder()
            .snapshotDate(today)
            .liquidAssets(current.getLiquidAssets())
            .creditCardDebt(current.getCreditCardDebt())
            .manualAssets(current.getManualAssetsTotal())
            .manualLiabilities(current.getManualLiabilities())
            .netWorth(current.getNetWorth())
            .build();
        return netWorthSnapshotRepository.findBySnapshotDate(today)
            .map(existing -> {
                existing.setLiquidAssets(current.getLiquidAssets());
                existing.setCreditCardDebt(current.getCreditCardDebt());
                existing.setManualAssets(current.getManualAssetsTotal());
                existing.setManualLiabilities(current.getManualLiabilities());
                existing.setNetWorth(current.getNetWorth());
                return netWorthSnapshotRepository.save(existing);
            })
            .orElseGet(() -> netWorthSnapshotRepository.save(snapshot));
    }

    public List<ManualAsset> getAllAssets() {
        return manualAssetRepository.findAll();
    }

    public ManualAsset addAsset(ManualAssetRequest req) {
        ManualAsset.AssetType at = ManualAsset.AssetType.valueOf(req.getAssetType().toUpperCase());
        ManualAsset.AssetClass ac = ManualAsset.AssetClass.valueOf(req.getAssetClass().toUpperCase());
        return manualAssetRepository.save(ManualAsset.builder()
            .name(req.getName())
            .assetType(at)
            .assetClass(ac)
            .currentValue(req.getCurrentValue())
            .notes(req.getNotes())
            .build());
    }

    public ManualAsset updateAsset(UUID id, ManualAssetRequest req) {
        ManualAsset a = manualAssetRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("ManualAsset not found: " + id));
        if (req.getName() != null) a.setName(req.getName());
        if (req.getCurrentValue() != null) a.setCurrentValue(req.getCurrentValue());
        if (req.getNotes() != null) a.setNotes(req.getNotes());
        if (req.getAssetType() != null) a.setAssetType(ManualAsset.AssetType.valueOf(req.getAssetType().toUpperCase()));
        if (req.getAssetClass() != null) a.setAssetClass(ManualAsset.AssetClass.valueOf(req.getAssetClass().toUpperCase()));
        return manualAssetRepository.save(a);
    }

    public void deleteAsset(UUID id) {
        manualAssetRepository.deleteById(id);
    }
}

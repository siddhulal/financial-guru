package com.financialguru.repository;

import com.financialguru.model.ManualAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ManualAssetRepository extends JpaRepository<ManualAsset, UUID> {

    List<ManualAsset> findByAssetTypeOrderByCurrentValueDesc(ManualAsset.AssetType assetType);
}

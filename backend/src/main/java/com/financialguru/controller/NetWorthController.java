package com.financialguru.controller;

import com.financialguru.dto.request.ManualAssetRequest;
import com.financialguru.dto.response.NetWorthResponse;
import com.financialguru.model.ManualAsset;
import com.financialguru.model.NetWorthSnapshot;
import com.financialguru.service.NetWorthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/networth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NetWorthController {

    private final NetWorthService netWorthService;

    @GetMapping
    public NetWorthResponse getNetWorth() {
        return netWorthService.getCurrentNetWorth();
    }

    @GetMapping("/history")
    public List<NetWorthSnapshot> getHistory() {
        return netWorthService.getHistory();
    }

    @PostMapping("/snapshot")
    public NetWorthSnapshot captureSnapshot() {
        return netWorthService.captureSnapshot();
    }

    @GetMapping("/assets")
    public List<ManualAsset> getAssets() {
        return netWorthService.getAllAssets();
    }

    @PostMapping("/assets")
    public ManualAsset addAsset(@RequestBody ManualAssetRequest req) {
        return netWorthService.addAsset(req);
    }

    @PutMapping("/assets/{id}")
    public ManualAsset updateAsset(@PathVariable UUID id, @RequestBody ManualAssetRequest req) {
        return netWorthService.updateAsset(id, req);
    }

    @DeleteMapping("/assets/{id}")
    public void deleteAsset(@PathVariable UUID id) {
        netWorthService.deleteAsset(id);
    }
}

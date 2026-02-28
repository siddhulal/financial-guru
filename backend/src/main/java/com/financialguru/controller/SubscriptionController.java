package com.financialguru.controller;

import com.financialguru.model.Subscription;
import com.financialguru.repository.SubscriptionRepository;
import com.financialguru.service.SubscriptionDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Manage detected recurring subscriptions")
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionDetectionService subscriptionDetectionService;

    @PostMapping("/detect")
    @Operation(summary = "Re-scan all transactions and detect subscriptions from scratch")
    public ResponseEntity<Map<String, Object>> detectAll() {
        int count = subscriptionDetectionService.detectAllSubscriptions();
        return ResponseEntity.ok(Map.of("detected", count));
    }

    @GetMapping
    @Operation(summary = "List all active subscriptions")
    public ResponseEntity<List<Subscription>> getAllSubscriptions() {
        return ResponseEntity.ok(subscriptionRepository.findByIsActiveTrueOrderByAnnualCostDesc());
    }

    @GetMapping("/duplicates")
    @Operation(summary = "List subscriptions detected on multiple cards")
    public ResponseEntity<List<Subscription>> getDuplicates() {
        return ResponseEntity.ok(subscriptionRepository.findByIsDuplicateTrueAndIsActiveTrue());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update subscription notes or status")
    public ResponseEntity<Subscription> updateSubscription(
        @PathVariable UUID id,
        @RequestBody Map<String, Object> updates
    ) {
        Subscription sub = subscriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Subscription not found: " + id));

        if (updates.containsKey("notes")) {
            sub.setNotes((String) updates.get("notes"));
        }
        if (updates.containsKey("isActive")) {
            sub.setIsActive((Boolean) updates.get("isActive"));
        }

        return ResponseEntity.ok(subscriptionRepository.save(sub));
    }
}

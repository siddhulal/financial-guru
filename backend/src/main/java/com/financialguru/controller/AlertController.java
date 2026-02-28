package com.financialguru.controller;

import com.financialguru.dto.response.AlertResponse;
import com.financialguru.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Manage financial alerts and notifications")
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @Operation(summary = "List all unresolved alerts")
    public ResponseEntity<List<AlertResponse>> getAllAlerts() {
        return ResponseEntity.ok(alertService.getAllUnresolvedAlerts());
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread alert count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", alertService.getUnreadCount()));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark alert as read")
    public ResponseEntity<AlertResponse> markAsRead(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.markAsRead(id));
    }

    @PutMapping("/{id}/resolve")
    @Operation(summary = "Mark alert as resolved")
    public ResponseEntity<AlertResponse> markAsResolved(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.markAsResolved(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Dismiss/delete an alert")
    public ResponseEntity<Void> deleteAlert(@PathVariable UUID id) {
        alertService.deleteAlert(id);
        return ResponseEntity.noContent().build();
    }
}

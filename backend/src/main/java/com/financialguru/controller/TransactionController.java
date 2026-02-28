package com.financialguru.controller;

import com.financialguru.dto.request.TransactionFilterRequest;
import com.financialguru.dto.response.TransactionResponse;
import com.financialguru.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Query and manage transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @Operation(summary = "List all transactions with filtering")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
        @ModelAttribute TransactionFilterRequest filter
    ) {
        return ResponseEntity.ok(transactionService.getTransactions(filter));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction detail")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getTransaction(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update transaction category or notes")
    public ResponseEntity<TransactionResponse> updateTransaction(
        @PathVariable UUID id,
        @RequestBody Map<String, Object> updates
    ) {
        return ResponseEntity.ok(transactionService.updateTransaction(id, updates));
    }

    @GetMapping("/anomalies")
    @Operation(summary = "Get flagged/anomalous transactions")
    public ResponseEntity<List<TransactionResponse>> getAnomalies() {
        return ResponseEntity.ok(transactionService.getAnomalies());
    }

    @GetMapping("/search")
    @Operation(summary = "Search transactions by text")
    public ResponseEntity<Page<TransactionResponse>> searchTransactions(
        @RequestParam String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        TransactionFilterRequest filter = new TransactionFilterRequest();
        filter.setSearch(q);
        filter.setPage(page);
        filter.setSize(size);
        return ResponseEntity.ok(transactionService.getTransactions(filter));
    }
}

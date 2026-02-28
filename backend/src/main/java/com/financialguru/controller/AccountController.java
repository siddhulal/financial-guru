package com.financialguru.controller;

import com.financialguru.dto.request.AccountRequest;
import com.financialguru.dto.response.AccountResponse;
import com.financialguru.dto.response.TransactionResponse;
import com.financialguru.dto.request.TransactionFilterRequest;
import com.financialguru.service.AccountService;
import com.financialguru.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Manage credit cards and bank accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    @GetMapping
    @Operation(summary = "List all active accounts")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @PostMapping
    @Operation(summary = "Add a new account/card")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountRequest request) {
        return ResponseEntity.ok(accountService.createAccount(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account details")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update account info")
    public ResponseEntity<AccountResponse> updateAccount(
        @PathVariable UUID id,
        @Valid @RequestBody AccountRequest request
    ) {
        return ResponseEntity.ok(accountService.updateAccount(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate an account")
    public ResponseEntity<Void> deactivateAccount(@PathVariable UUID id) {
        accountService.deactivateAccount(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Get transactions for an account")
    public ResponseEntity<Page<TransactionResponse>> getAccountTransactions(
        @PathVariable UUID id,
        @ModelAttribute TransactionFilterRequest filter
    ) {
        filter.setAccountId(id);
        return ResponseEntity.ok(transactionService.getTransactions(filter));
    }
}

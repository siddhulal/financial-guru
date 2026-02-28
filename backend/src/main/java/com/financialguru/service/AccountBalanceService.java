package com.financialguru.service;

import com.financialguru.model.Account;
import com.financialguru.model.AccountBalanceSnapshot;
import com.financialguru.model.Alert;
import com.financialguru.repository.AccountBalanceSnapshotRepository;
import com.financialguru.repository.AccountRepository;
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
public class AccountBalanceService {

    private final AccountRepository accountRepository;
    private final AccountBalanceSnapshotRepository accountBalanceSnapshotRepository;
    private final AlertService alertService;

    public void captureSnapshots() {
        LocalDate today = LocalDate.now();
        List<Account> accounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        for (Account a : accounts) {
            if (a.getCurrentBalance() == null) continue;
            accountBalanceSnapshotRepository.findByAccountIdAndSnapshotDate(a.getId(), today)
                    .ifPresentOrElse(
                            existing -> {
                                existing.setBalance(a.getCurrentBalance());
                                accountBalanceSnapshotRepository.save(existing);
                            },
                            () -> accountBalanceSnapshotRepository.save(AccountBalanceSnapshot.builder()
                                    .account(a)
                                    .snapshotDate(today)
                                    .balance(a.getCurrentBalance())
                                    .build()));
            // Check for low balance alert
            if ((a.getType() == Account.AccountType.CHECKING
                    || a.getType() == Account.AccountType.SAVINGS)
                    && a.getCurrentBalance().compareTo(BigDecimal.valueOf(500)) < 0) {
                alertService.createAlert(Alert.AlertType.ANOMALY, Alert.AlertSeverity.HIGH,
                        "Low Balance: " + a.getName(),
                        String.format("%s balance is $%.2f — below $500 threshold.",
                                a.getName(), a.getCurrentBalance()),
                        a, null, null);
            }
        }
    }

    public List<AccountBalanceSnapshot> getHistory(UUID accountId, int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        return accountBalanceSnapshotRepository
                .findByAccountIdAndSnapshotDateAfterOrderBySnapshotDateAsc(accountId, since);
    }
}

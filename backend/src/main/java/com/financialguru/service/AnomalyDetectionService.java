package com.financialguru.service;

import com.financialguru.model.Account;
import com.financialguru.model.Alert;
import com.financialguru.model.Transaction;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionService {

    private final TransactionRepository transactionRepository;
    private final AlertService alertService;
    private final OllamaService ollamaService;

    private static final BigDecimal LARGE_TRANSACTION_THRESHOLD = new BigDecimal("500.00");
    private static final int DUPLICATE_WINDOW_DAYS = 7;
    private static final double SPIKE_MULTIPLIER = 2.5;

    @Transactional
    public List<Transaction> detectAnomalies(List<Transaction> newTransactions, Account account) {
        List<Transaction> flagged = new ArrayList<>();

        // Rule 1: Large transactions
        flagged.addAll(detectLargeTransactions(newTransactions, account));

        // Rule 2: Duplicate charges (same amount, same merchant, within 7 days)
        flagged.addAll(detectDuplicateCharges(newTransactions, account));

        // Rule 3: Spending spikes for a merchant (> 2.5x average)
        flagged.addAll(detectSpendingSpikes(newTransactions, account));

        return flagged;
    }

    private List<Transaction> detectLargeTransactions(List<Transaction> transactions, Account account) {
        return transactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount().compareTo(LARGE_TRANSACTION_THRESHOLD) > 0)
            .filter(t -> t.getType() == Transaction.TransactionType.DEBIT)
            .peek(t -> {
                t.setIsFlagged(true);
                t.setFlagReason("Large transaction: $" + t.getAmount());
                alertService.createAlert(
                    Alert.AlertType.LARGE_TRANSACTION,
                    t.getAmount().compareTo(new BigDecimal("1000")) > 0
                        ? Alert.AlertSeverity.HIGH : Alert.AlertSeverity.MEDIUM,
                    "Large Transaction Detected",
                    String.format("$%.2f charge at %s on %s",
                        t.getAmount(), t.getMerchantName(), t.getTransactionDate()),
                    account, t, null
                );
            })
            .collect(Collectors.toList());
    }

    private List<Transaction> detectDuplicateCharges(List<Transaction> transactions, Account account) {
        List<Transaction> flagged = new ArrayList<>();

        // Group by merchant and amount
        Map<String, List<Transaction>> byMerchantAmount = transactions.stream()
            .filter(t -> t.getMerchantName() != null)
            .collect(Collectors.groupingBy(t ->
                t.getMerchantName() + ":" + t.getAmount().toPlainString()));

        for (Map.Entry<String, List<Transaction>> entry : byMerchantAmount.entrySet()) {
            List<Transaction> group = entry.getValue();
            if (group.size() > 1) {
                // Check if within DUPLICATE_WINDOW_DAYS of each other
                for (int i = 0; i < group.size(); i++) {
                    for (int j = i + 1; j < group.size(); j++) {
                        Transaction t1 = group.get(i);
                        Transaction t2 = group.get(j);
                        long daysBetween = Math.abs(
                            t1.getTransactionDate().toEpochDay() - t2.getTransactionDate().toEpochDay());

                        if (daysBetween <= DUPLICATE_WINDOW_DAYS) {
                            t2.setIsFlagged(true);
                            t2.setFlagReason("Possible duplicate charge (same as " + t1.getTransactionDate() + ")");
                            flagged.add(t2);

                            alertService.createAlert(
                                Alert.AlertType.DUPLICATE_CHARGE,
                                Alert.AlertSeverity.HIGH,
                                "Possible Duplicate Charge",
                                String.format("$%.2f at %s appears twice within %d days (%s and %s)",
                                    t2.getAmount(), t2.getMerchantName(), daysBetween,
                                    t1.getTransactionDate(), t2.getTransactionDate()),
                                account, t2, null
                            );
                        }
                    }
                }
            }
        }

        return flagged;
    }

    private List<Transaction> detectSpendingSpikes(List<Transaction> transactions, Account account) {
        List<Transaction> flagged = new ArrayList<>();

        // For each merchant, check if current charge is > 2.5x historical average
        Map<String, List<Transaction>> byMerchant = transactions.stream()
            .filter(t -> t.getMerchantName() != null)
            .collect(Collectors.groupingBy(Transaction::getMerchantName));

        for (Map.Entry<String, List<Transaction>> entry : byMerchant.entrySet()) {
            String merchant = entry.getKey();
            List<Transaction> newTxns = entry.getValue();

            // Get historical transactions for this merchant (last 6 months)
            List<Transaction> historical = transactionRepository.findByAccountAndMerchantSince(
                account.getId(), merchant, LocalDate.now().minusMonths(6));

            if (historical.size() < 3) continue; // Not enough history

            BigDecimal avgHistorical = historical.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(historical.size()), 2, RoundingMode.HALF_UP);

            for (Transaction t : newTxns) {
                if (t.getAmount().compareTo(avgHistorical.multiply(BigDecimal.valueOf(SPIKE_MULTIPLIER))) > 0) {
                    t.setIsFlagged(true);
                    t.setFlagReason(String.format("Spending spike: $%.2f vs avg $%.2f",
                        t.getAmount(), avgHistorical));
                    flagged.add(t);

                    alertService.createAlert(
                        Alert.AlertType.OVERCHARGE,
                        Alert.AlertSeverity.HIGH,
                        "Spending Spike Detected",
                        String.format("$%.2f at %s is %.1fx your usual amount of $%.2f",
                            t.getAmount(), merchant,
                            t.getAmount().divide(avgHistorical, 1, RoundingMode.HALF_UP).doubleValue(),
                            avgHistorical),
                        account, t, null
                    );
                }
            }
        }

        return flagged;
    }
}

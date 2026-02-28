package com.financialguru.service;

import com.financialguru.model.Account;
import com.financialguru.model.Statement;
import com.financialguru.model.Transaction;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.StatementRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatementService {

    private final StatementRepository statementRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PdfExtractionService pdfExtractionService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final SubscriptionDetectionService subscriptionDetectionService;

    @Value("${app.upload.dir:./uploads/statements}")
    private String uploadDir;

    @Transactional
    public Statement uploadStatement(MultipartFile file, UUID accountId) throws IOException {
        // Create upload directory if needed
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);

        // Save file
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath);

        Account account = accountId != null
            ? accountRepository.findById(accountId).orElse(null)
            : null;

        Statement statement = Statement.builder()
            .account(account)
            .fileName(file.getOriginalFilename())
            .filePath(filePath.toString())
            .status(Statement.StatementStatus.PENDING)
            .build();

        Statement saved = statementRepository.save(statement);
        processStatementAsync(saved.getId());
        return saved;
    }

    @Async
    public void processStatementAsync(UUID statementId) {
        try {
            Statement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new RuntimeException("Statement not found: " + statementId));

            statement.setStatus(Statement.StatementStatus.PROCESSING);
            statementRepository.save(statement);

            File pdfFile = new File(statement.getFilePath());
            Account account = statement.getAccount();

            // Auto-create account if none was provided at upload time
            if (account == null) {
                account = autoCreateAccount(pdfFile, statement);
            }

            List<Transaction> transactions = pdfExtractionService.extractTransactions(
                pdfFile, statement, account);

            // Persist any account metadata (APR, promo APR, etc.) updated during extraction.
            // Also carry forward the payment due day so the dashboard always shows the
            // next upcoming payment even when no new statement has been uploaded yet.
            if (account != null) {
                if (statement.getPaymentDueDate() != null) {
                    int dueDay = statement.getPaymentDueDate().getDayOfMonth();
                    account.setPaymentDueDay(dueDay);
                    log.info("Account {}: payment due day set to {} (from statement due date {})",
                        account.getName(), dueDay, statement.getPaymentDueDate());
                }
                accountRepository.save(account);
                log.info("Saved account {} with updated metadata", account.getId());
            }

            // Set statement dates from transactions
            if (!transactions.isEmpty()) {
                LocalDate minDate = transactions.stream()
                    .map(Transaction::getTransactionDate)
                    .min(LocalDate::compareTo).orElse(null);
                LocalDate maxDate = transactions.stream()
                    .map(Transaction::getTransactionDate)
                    .max(LocalDate::compareTo).orElse(null);
                statement.setStartDate(minDate);
                statement.setEndDate(maxDate);
                statement.setStatementMonth(minDate != null ? minDate.withDayOfMonth(1) : null);
            }

            // Save transactions
            List<Transaction> saved = transactionRepository.saveAll(transactions);
            log.info("Saved {} transactions for statement {}", saved.size(), statementId);

            // Run anomaly detection and subscription detection
            if (account != null) {
                anomalyDetectionService.detectAnomalies(saved, account);
                subscriptionDetectionService.detectSubscriptions(saved, account);
            }

            statement.setStatus(Statement.StatementStatus.COMPLETED);
            statementRepository.save(statement);
            log.info("Statement {} processing complete", statementId);

        } catch (Exception e) {
            log.error("Failed to process statement {}: {}", statementId, e.getMessage(), e);
            statementRepository.findById(statementId).ifPresent(s -> {
                s.setStatus(Statement.StatementStatus.FAILED);
                s.setErrorMessage(e.getMessage());
                statementRepository.save(s);
            });
        }
    }

    public List<Statement> getAllStatements() {
        return statementRepository.findAllOrderByCreatedAtDesc();
    }

    public Statement getStatement(UUID id) {
        return statementRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Statement not found: " + id));
    }

    @Transactional
    public void deleteStatement(UUID id) {
        Statement statement = getStatement(id);
        List<Transaction> existing = transactionRepository.findByStatementId(id);
        if (!existing.isEmpty()) transactionRepository.deleteAll(existing);
        statementRepository.delete(statement);
        log.info("Deleted statement {} and {} transactions", id, existing.size());
    }

    @Transactional
    public void reprocessStatement(UUID id) {
        Statement statement = getStatement(id);
        // Delete existing transactions first so reprocessing never creates duplicates
        List<Transaction> existing = transactionRepository.findByStatementId(id);
        if (!existing.isEmpty()) {
            transactionRepository.deleteAll(existing);
            log.info("Deleted {} existing transactions before reprocessing statement {}", existing.size(), id);
        }
        statement.setStatus(Statement.StatementStatus.PENDING);
        statement.setErrorMessage(null);
        // Reset parsed fields so they get re-extracted cleanly
        statement.setPaymentDueDate(null);
        statement.setMinimumPayment(null);
        statement.setStartDate(null);
        statement.setEndDate(null);
        statement.setYtdTotalFees(null);
        statement.setYtdTotalInterest(null);
        statement.setYtdYear(null);
        statementRepository.save(statement);
        processStatementAsync(id);
    }

    // Generic last4 pattern — covers most bank statement formats
    private static final java.util.regex.Pattern LAST4_FROM_TEXT = java.util.regex.Pattern.compile(
        "(?i)(?:account|card)\\s*(?:number|ending|#)[:\\s]+(?:[Xx*]{4}[\\s-]*){2,3}(\\d{4})"
    );

    /**
     * Detects institution from the PDF and finds or creates a matching account.
     * Matches by institution + last4 to correctly distinguish multiple cards
     * from the same bank (e.g., two Chase cards).
     */
    @Transactional
    private Account autoCreateAccount(File pdfFile, Statement statement) {
        String text = pdfExtractionService.extractText(pdfFile);
        String institution = pdfExtractionService.detectInstitution(text);

        if ("GENERIC".equals(institution)) {
            log.info("Could not detect institution — statement will have no account");
            return null;
        }

        // Try to extract last4 from text early for matching
        String last4 = null;
        java.util.regex.Matcher last4M = LAST4_FROM_TEXT.matcher(text);
        if (last4M.find()) {
            last4 = last4M.group(1);
            log.info("Detected last4 = {} for institution {}", last4, institution);
        }

        // Match by institution + last4 first (most precise)
        if (last4 != null) {
            final String l4 = last4;
            List<Account> existing = accountRepository.findByInstitution(institution);
            java.util.Optional<Account> exact = existing.stream()
                .filter(a -> l4.equals(a.getLast4()))
                .findFirst();
            if (exact.isPresent()) {
                Account found = exact.get();
                statement.setAccount(found);
                statementRepository.save(statement);
                log.info("Auto-linked statement to {} account {} (last4 match)", institution, found.getName());
                return found;
            }
        }

        // No exact match — create a new account
        String displayName = institutionDisplayName(institution);
        if (last4 != null) displayName = displayName.replace(" Card", " ···" + last4);

        Account newAccount = Account.builder()
            .name(displayName)
            .institution(institution)
            .last4(last4)
            .type(Account.AccountType.CREDIT_CARD)
            .isActive(true)
            .build();
        newAccount = accountRepository.save(newAccount);

        statement.setAccount(newAccount);
        statementRepository.save(statement);
        log.info("Auto-created {} account '{}' for statement", institution, displayName);
        return newAccount;
    }

    private String institutionDisplayName(String institution) {
        return switch (institution) {
            case "AMEX"           -> "American Express Card";
            case "CHASE"          -> "Chase Card";
            case "CITI"           -> "Citi Card";
            case "WELLS_FARGO"    -> "Wells Fargo Card";
            case "CAPITAL_ONE"    -> "Capital One Card";
            case "DISCOVER"       -> "Discover Card";
            case "BANK_OF_AMERICA"-> "Bank of America Card";
            case "GOLDMAN_SACHS"  -> "Apple Card";
            default               -> institution + " Card";
        };
    }

    @Transactional
    public Statement assignAccount(UUID statementId, UUID accountId) {
        Statement statement = getStatement(statementId);
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));

        statement.setAccount(account);
        statementRepository.save(statement);

        // Back-fill all transactions for this statement with the account
        List<Transaction> transactions = transactionRepository.findByStatementId(statementId);
        transactions.forEach(t -> t.setAccount(account));
        transactionRepository.saveAll(transactions);

        log.info("Assigned account {} to statement {} ({} transactions updated)",
            account.getName(), statementId, transactions.size());
        return statement;
    }
}

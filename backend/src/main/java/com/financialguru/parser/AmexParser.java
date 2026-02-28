package com.financialguru.parser;

import com.financialguru.model.Account;
import com.financialguru.model.Statement;
import com.financialguru.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * American Express statement parser.
 *
 * Amex "Screen Reader Optimized" PDF format:
 *   Date line:    MM/DD/YY[*]  Description [City State]  $Amount
 *   Follow lines: phone / reference (skipped)
 *
 * Payments have an asterisk after the date: 01/27/26*
 */
@Component
@Slf4j
public class AmexParser extends GenericPdfParser {

    // MM/DD/YY or MM/DD/YY* followed by description and $Amount at end of line
    private static final Pattern AMEX_TXN = Pattern.compile(
        "^(\\d{2}/\\d{2}/\\d{2})\\*?\\s+(.+?)\\s+(-?\\$[\\d,]+\\.\\d{2})\\s*$"
    );

    // ── Payment summary ───────────────────────────────────────────────────────
    private static final Pattern MIN_PAYMENT = Pattern.compile(
        "(?i)minimum\\s+payment\\s+due\\s+\\$?([\\d,]+\\.\\d{2})"
    );
    private static final Pattern PAYMENT_DUE_DATE = Pattern.compile(
        "(?i)payment\\s+due\\s+date\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── YTD totals ────────────────────────────────────────────────────────────
    // "Total Fees in 2026  $0.00"
    private static final Pattern YTD_FEES = Pattern.compile(
        "(?i)total\\s+fees\\s+in\\s+(\\d{4})\\s+\\$?([\\d,]+\\.\\d{2})"
    );
    // "Total Interest in 2026  $72.72"
    private static final Pattern YTD_INTEREST = Pattern.compile(
        "(?i)total\\s+interest\\s+in\\s+(\\d{4})\\s+\\$?([\\d,]+\\.\\d{2})"
    );

    // ── APR ───────────────────────────────────────────────────────────────────
    // "Purchases  04/01/2023    28.49%    Variable"
    private static final Pattern PURCHASES_APR = Pattern.compile(
        "(?i)\\bpurchases\\b[^\\n]{0,80}?(\\d{1,2}\\.\\d{2})%"
    );

    // ── Credit limit / available credit / balance ─────────────────────────────
    private static final Pattern CREDIT_LIMIT = Pattern.compile(
        "(?i)credit\\s+limit\\s+\\$?([\\d,]+\\.\\d{2})"
    );
    private static final Pattern AVAILABLE_CREDIT = Pattern.compile(
        "(?i)available\\s+credit\\s+\\$?([\\d,]+\\.\\d{2})"
    );
    // "New Balance $2,471.93" — match only at start of line to avoid prose mentions
    private static final Pattern NEW_BALANCE = Pattern.compile(
        "(?im)^new\\s+balance\\s+\\$([\\d,]+\\.\\d{2})\\s*$"
    );
    // "Account Ending 9-04001" or "Card Ending 9-04001" → last 4 digits
    private static final Pattern ACCOUNT_ENDING = Pattern.compile(
        "(?i)(?:account|card)\\s+ending\\s+[\\d-]*(\\d{4})\\s*$",
        Pattern.MULTILINE
    );

    @Override
    public boolean supports(String institution) {
        return "AMEX".equals(institution);
    }

    @Override
    public void extractAccountInfo(String pdfText, Account account) {
        if (account == null || pdfText == null) return;

        // Always mark as credit card (Amex only issues credit cards)
        account.setType(Account.AccountType.CREDIT_CARD);

        // APR — look in the Interest Charge Calculation section
        if (account.getApr() == null) {
            int sectionStart = pdfText.toLowerCase().indexOf("interest charge calculation");
            String section = sectionStart >= 0
                ? pdfText.substring(sectionStart, Math.min(sectionStart + 2000, pdfText.length()))
                : pdfText;
            Matcher m = PURCHASES_APR.matcher(section);
            if (m.find()) {
                try {
                    account.setApr(new BigDecimal(m.group(1)));
                    log.info("Amex: extracted APR {}%", m.group(1));
                } catch (Exception e) {
                    log.debug("Amex: could not parse APR: {}", e.getMessage());
                }
            }
        }

        // Credit limit
        if (account.getCreditLimit() == null) {
            Matcher m = CREDIT_LIMIT.matcher(pdfText);
            if (m.find()) {
                try {
                    account.setCreditLimit(parseAmount(m.group(1)));
                    log.info("Amex: extracted credit limit {}", m.group(1));
                } catch (Exception e) {
                    log.debug("Amex: could not parse credit limit: {}", e.getMessage());
                }
            }
        }

        // Available credit
        if (account.getAvailableCredit() == null) {
            Matcher m = AVAILABLE_CREDIT.matcher(pdfText);
            if (m.find()) {
                try {
                    account.setAvailableCredit(parseAmount(m.group(1)));
                    log.info("Amex: extracted available credit {}", m.group(1));
                } catch (Exception e) {
                    log.debug("Amex: could not parse available credit: {}", e.getMessage());
                }
            }
        }

        // Current balance — always update from latest statement
        Matcher balM = NEW_BALANCE.matcher(pdfText);
        if (balM.find()) {
            try {
                account.setCurrentBalance(parseAmount(balM.group(1)));
                log.info("Amex: extracted current balance {}", balM.group(1));
            } catch (Exception e) {
                log.debug("Amex: could not parse new balance: {}", e.getMessage());
            }
        }

        // Last 4 digits
        if (account.getLast4() == null) {
            Matcher last4M = ACCOUNT_ENDING.matcher(pdfText);
            if (last4M.find()) {
                account.setLast4(last4M.group(1));
                log.info("Amex: extracted last4 = {}", last4M.group(1));
            }
        }
    }

    @Override
    public List<Transaction> parse(String pdfText, Statement statement, Account account) {
        log.info("Using Amex-specific parser");

        // Always extract payment/YTD info regardless of transaction parse success
        extractPaymentSummary(pdfText, statement);
        extractYtdTotals(pdfText, statement);

        List<Transaction> transactions = new ArrayList<>();
        boolean inChargesSection = false;
        boolean inPaymentsSection = false;
        boolean inInterestSection = false;

        String[] lines = pdfText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) continue;

            // Section detection
            String lower = line.toLowerCase();
            if (lower.startsWith("new charges") || lower.startsWith("charges")) {
                inChargesSection = true; inPaymentsSection = false; inInterestSection = false;
                continue;
            }
            if (lower.startsWith("payments") || lower.startsWith("payments and credits")) {
                inPaymentsSection = true; inChargesSection = false; inInterestSection = false;
                continue;
            }
            if (lower.startsWith("interest charged") || lower.startsWith("fees")) {
                inInterestSection = true; inChargesSection = false; inPaymentsSection = false;
                continue;
            }
            if (lower.startsWith("about trailing interest") || lower.startsWith("important notices")) {
                inChargesSection = false; inPaymentsSection = false; inInterestSection = false;
            }

            Matcher m = AMEX_TXN.matcher(line);
            if (!m.matches()) continue;

            try {
                String dateStr = m.group(1);
                String description = m.group(2).trim();
                String amountStr = m.group(3).trim();

                // Skip header rows
                if (description.equalsIgnoreCase("description") || description.equalsIgnoreCase("amount")) continue;
                // Skip summary/total lines
                if (description.toLowerCase().startsWith("total ") || description.toLowerCase().startsWith("new balance")) continue;

                LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yy"));
                BigDecimal amount = parseAmount(amountStr.replace("$", ""));

                // Determine transaction type
                Transaction.TransactionType type;
                if (inInterestSection) {
                    type = Transaction.TransactionType.INTEREST;
                } else if (inPaymentsSection
                        || description.toUpperCase().contains("PAYMENT RECEIVED")
                        || description.toUpperCase().contains("AUTOPAY")
                        || amount.compareTo(BigDecimal.ZERO) < 0) {
                    type = Transaction.TransactionType.CREDIT;
                } else {
                    type = Transaction.TransactionType.DEBIT;
                }

                // Strip city/state suffix from description (e.g., "NETFLIX SAN FRANCISCO CA" → "NETFLIX")
                String merchant = normalizeMerchant(description);
                String category = categorize(merchant, type);

                Transaction t = Transaction.builder()
                    .account(account)
                    .statement(statement)
                    .transactionDate(date)
                    .description(description)
                    .merchantName(merchant)
                    .amount(amount.abs())
                    .type(type)
                    .category(category)
                    .build();

                transactions.add(t);
                log.debug("Amex parsed: {} | {} | {}", date, merchant, amount);
            } catch (Exception e) {
                log.debug("Amex parser: could not parse line: {} — {}", line, e.getMessage());
            }
        }

        log.info("Amex parser extracted {} transactions", transactions.size());

        if (transactions.isEmpty()) {
            log.warn("Amex parser found 0 transactions — falling back to generic");
            return super.parse(pdfText, statement, account);
        }
        return transactions;
    }

    // ── Merchant normalization ────────────────────────────────────────────────

    // Strip "AplPay " prefix Amex adds for Apple Pay transactions
    private static final Pattern APLPAY_PREFIX = Pattern.compile("(?i)^AplPay\\s+");
    // Trailing city/state: "SAN FRANCISCO CA" or just "CA"
    private static final Pattern TRAILING_LOCATION = Pattern.compile(
        "(?:\\s+[A-Z][A-Za-z]{2,}){0,2}\\s+[A-Z]{2}\\s*$"
    );

    @Override
    protected String normalizeMerchant(String description) {
        if (description == null) return null;
        String name = description.trim();

        // Remove "AplPay" prefix
        name = APLPAY_PREFIX.matcher(name).replaceFirst("").trim();

        // Delegate to parent for common cleanup (reference numbers, bank codes)
        name = super.normalizeMerchant(name);

        // Strip trailing city/state suffix
        String stripped = TRAILING_LOCATION.matcher(name).replaceAll("").trim();
        if (stripped.length() >= 2) name = stripped;

        return name.isEmpty() ? description.trim() : name;
    }

    // ── Payment summary extraction ────────────────────────────────────────────

    private void extractPaymentSummary(String pdfText, Statement statement) {
        if (statement == null) return;

        Matcher minM = MIN_PAYMENT.matcher(pdfText);
        if (minM.find()) {
            try {
                statement.setMinimumPayment(parseAmount(minM.group(1)));
                log.info("Amex: minimum payment = {}", minM.group(1));
            } catch (Exception e) {
                log.debug("Amex: could not parse minimum payment");
            }
        }

        Matcher dueM = PAYMENT_DUE_DATE.matcher(pdfText);
        if (dueM.find()) {
            LocalDate d = parseDate(dueM.group(1));
            if (d != null) {
                statement.setPaymentDueDate(d);
                log.info("Amex: payment due date = {}", d);
            }
        }
    }

    private void extractYtdTotals(String pdfText, Statement statement) {
        if (statement == null) return;

        Matcher feesM = YTD_FEES.matcher(pdfText);
        if (feesM.find()) {
            try {
                statement.setYtdTotalFees(parseAmount(feesM.group(2)));
                statement.setYtdYear(Integer.parseInt(feesM.group(1)));
                log.info("Amex: YTD fees ({}) = {}", feesM.group(1), feesM.group(2));
            } catch (Exception e) {
                log.debug("Amex: could not parse YTD fees");
            }
        }

        Matcher intM = YTD_INTEREST.matcher(pdfText);
        if (intM.find()) {
            try {
                statement.setYtdTotalInterest(parseAmount(intM.group(2)));
                if (statement.getYtdYear() == null) {
                    statement.setYtdYear(Integer.parseInt(intM.group(1)));
                }
                log.info("Amex: YTD interest ({}) = {}", intM.group(1), intM.group(2));
            } catch (Exception e) {
                log.debug("Amex: could not parse YTD interest");
            }
        }
    }

    // Reuse GenericPdfParser.parseDate but also handle MM/DD/YY
    @Override
    protected LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("MM/dd/yy"));
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        } catch (DateTimeParseException ignored) {}
        return super.parseDate(dateStr);
    }
}

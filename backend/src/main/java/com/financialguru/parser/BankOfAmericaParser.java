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
import java.util.regex.PatternSyntaxException;

/**
 * Parser for Bank of America credit card PDF statements (text-based and OCR).
 *
 * BofA credit card lines look like (after PDFBox/OCR extraction):
 *   01/02  01/04  AMAZON.COM*AMZN.COM/BILL  WA            45.99
 *   01/05  01/07  NETFLIX.COM               CA             15.99
 *   01/10  01/10  ONLINE PAYMENT THANK YOU            -1,500.00
 *
 * Two date columns (MM/dd, no year), description, optional state/city, amount.
 * Year is inferred from the statement closing-date header.
 */
@Component
@Slf4j
public class BankOfAmericaParser extends GenericPdfParser {

    // Two dates + description + amount (1+ spaces before amount, comma or dot decimal)
    private static final Pattern BOFA_TWO_DATE = Pattern.compile(
        "^(\\d{2}/\\d{2})\\s+(\\d{2}/\\d{2})\\s+(.+?)\\s+(-?\\$?[\\d,]+[.,]\\d{2})\\s*$"
    );

    // Single date + description + amount (fallback)
    private static final Pattern BOFA_ONE_DATE = Pattern.compile(
        "^(\\d{2}/\\d{2})\\s+(.+?)\\s+(-?\\$?[\\d,]+[.,]\\d{2})\\s*$"
    );

    // Strip trailing 15-20 digit reference numbers
    private static final Pattern REF_NUMBER = Pattern.compile("\\s+\\d{15,20}\\s*$");

    // Leading post-date that OCR bleeds into description: "02/08 Description"
    private static final Pattern LEADING_DATE = Pattern.compile("^\\d{2}/\\d{2}\\s+");

    // Trailing card digit segments: " 0317 3266"
    private static final Pattern TRAILING_CARD_DIGITS = Pattern.compile("(\\s+\\d{4}){1,2}\\s*$");

    // Trailing BofA location suffix: optional city words + state code
    // e.g. "CHARLOTTE NC", "CHARLOT CHARLOTTE NC", "CA"
    // Using {0,2} to avoid stripping real merchant words like "BROTHERS"
    private static final Pattern TRAILING_LOCATION = Pattern.compile(
        "(?:\\s+[A-Z]{3,}){0,2}\\s+[A-Z]{2}\\s*$"
    );

    // UPC codes embedded in merchant name
    private static final Pattern UPC_CODE = Pattern.compile("(?i)\\s+UPC#?\\s*\\d+");

    // ── Payment summary patterns ─────────────────────────────────────────────
    // "Minimum Payment Due $25.00" or "Total Minimum Payment Due $25.00"
    private static final Pattern MIN_PAYMENT = Pattern.compile(
        "(?i)(?:total\\s+)?minimum\\s+payment\\s+due[:\\s]+\\$?([\\d,]+\\.\\d{2})"
    );
    // "Payment Due Date  03/21/2026"
    private static final Pattern PAYMENT_DUE_DATE = Pattern.compile(
        "(?i)payment\\s+due\\s+date[:\\s]+(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── YTD totals patterns ──────────────────────────────────────────────────
    // "Total fees charged in 2026  $0.00"
    private static final Pattern YTD_FEES = Pattern.compile(
        "(?i)total\\s+fees\\s+charged\\s+in\\s+(\\d{4})\\s+\\$?([\\d,]+\\.\\d{2})"
    );
    // "Total interest charged in 2026  $0.00"
    private static final Pattern YTD_INTEREST = Pattern.compile(
        "(?i)total\\s+interest\\s+charged\\s+in\\s+(\\d{4})\\s+\\$?([\\d,]+\\.\\d{2})"
    );

    // ── Account type detection ───────────────────────────────────────────────
    // "Total Credit Line $4,500.00" — only appears on credit card statements
    private static final Pattern CREDIT_LINE = Pattern.compile(
        "(?i)total\\s+credit\\s+line"
    );

    // ── Balance / last4 ──────────────────────────────────────────────────────
    // "New Balance Total $533.56"
    private static final Pattern NEW_BALANCE = Pattern.compile(
        "(?i)new\\s+balance\\s+total\\s+\\$?([\\d,]+\\.\\d{2})"
    );
    // "Account# 5524 3317 8442 3266" → last 4 = "3266"
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile(
        "(?i)account\\s*#?\\s+(?:\\d{4}\\s+){2,3}(\\d{4})(?:\\s|$)"
    );

    // ── APR extraction patterns ──────────────────────────────────────────────
    // Matches "Purchases  20.74%V" or "Purchases    20.74%"
    private static final Pattern PURCHASES_APR = Pattern.compile(
        "(?i)\\bpurchases\\b[^\\n]{0,80}?(\\d{1,2}\\.\\d{2})%"
    );
    // Matches "Promotional APR  5.99%  ...  11/08/2026" (all on one line or across short span)
    private static final Pattern PROMO_APR_RATE = Pattern.compile(
        "(?i)\\bpromotional\\b.*?(\\d{1,2}\\.\\d{2})%"
    );
    private static final Pattern PROMO_APR_DATE = Pattern.compile(
        "(?i)\\bpromotional\\b[^\\n]*(\\d{1,2}/\\d{1,2}/\\d{4})"
    );
    // Also handles date on next line after promotional APR line
    private static final Pattern DATE_ANYWHERE = Pattern.compile(
        "(\\d{1,2}/\\d{1,2}/\\d{4})"
    );

    // ── Closing date patterns in statement header ────────────────────────────
    private static final Pattern CLOSING_DATE = Pattern.compile(
        "(?i)closing\\s+date[:\\s]+([\\d]{1,2}/[\\d]{1,2}/[\\d]{2,4})"
    );
    private static final Pattern OPENING_CLOSING = Pattern.compile(
        "(?i)opening.*closing.*?(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );
    private static final Pattern PERIOD_RANGE = Pattern.compile(
        "(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+(?:through|to|-|–)\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    @Override
    public boolean supports(String institution) {
        return "BANK_OF_AMERICA".equals(institution);
    }

    /**
     * Extracts APR and promotional APR info from the "Interest Charge Calculation" section.
     * Only sets values that are not already present on the account (non-destructive).
     */
    @Override
    public void extractAccountInfo(String pdfText, Account account) {
        if (account == null || pdfText == null) return;

        // Focus on the "Interest Charge Calculation" section to avoid false positives
        int sectionStart = pdfText.toLowerCase().indexOf("interest charge calculation");
        String section = sectionStart >= 0
            ? pdfText.substring(sectionStart, Math.min(sectionStart + 2000, pdfText.length()))
            : pdfText;

        // Standard purchases APR (e.g. "Purchases  20.74%V")
        if (account.getApr() == null) {
            Matcher aprM = PURCHASES_APR.matcher(section);
            if (aprM.find()) {
                try {
                    account.setApr(new java.math.BigDecimal(aprM.group(1)));
                    log.info("Extracted purchases APR: {}%", aprM.group(1));
                } catch (Exception e) {
                    log.debug("Could not parse APR value: {}", e.getMessage());
                }
            }
        }

        // Promotional APR rate (e.g. "Promotional APR  5.99%")
        if (account.getPromoApr() == null) {
            Matcher promoM = PROMO_APR_RATE.matcher(section);
            if (promoM.find()) {
                try {
                    account.setPromoApr(new java.math.BigDecimal(promoM.group(1)));
                    log.info("Extracted promo APR: {}%", promoM.group(1));
                } catch (Exception e) {
                    log.debug("Could not parse promo APR value: {}", e.getMessage());
                }
            }
        }

        // Promotional APR end date — first try same-line match, then look nearby
        if (account.getPromoAprEndDate() == null) {
            Matcher dateMOnLine = PROMO_APR_DATE.matcher(section);
            if (dateMOnLine.find()) {
                LocalDate d = parseFullDate(dateMOnLine.group(1));
                if (d != null) {
                    account.setPromoAprEndDate(d);
                    log.info("Extracted promo APR end date: {}", d);
                }
            } else {
                // Date may be on the next line — scan up to 300 chars after the promo rate match
                Matcher promoM2 = PROMO_APR_RATE.matcher(section);
                if (promoM2.find()) {
                    int from = promoM2.end();
                    int to = Math.min(from + 300, section.length());
                    Matcher dateM = DATE_ANYWHERE.matcher(section.substring(from, to));
                    if (dateM.find()) {
                        LocalDate d = parseFullDate(dateM.group(1));
                        if (d != null) {
                            account.setPromoAprEndDate(d);
                            log.info("Extracted promo APR end date (nearby): {}", d);
                        }
                    }
                }
            }
        }

        // Detect credit card vs checking/savings from statement content
        if (CREDIT_LINE.matcher(pdfText).find()) {
            account.setType(Account.AccountType.CREDIT_CARD);
            log.info("Detected account type: CREDIT_CARD (found 'Total Credit Line' in statement)");
        }

        // Current balance — always update from latest statement
        Matcher balM = NEW_BALANCE.matcher(pdfText);
        if (balM.find()) {
            try {
                account.setCurrentBalance(parseAmount(balM.group(1)));
                log.info("BofA: extracted current balance {}", balM.group(1));
            } catch (Exception e) {
                log.debug("BofA: could not parse new balance: {}", e.getMessage());
            }
        }

        // Last 4 digits
        if (account.getLast4() == null) {
            Matcher last4M = ACCOUNT_NUMBER.matcher(pdfText);
            if (last4M.find()) {
                account.setLast4(last4M.group(1));
                log.info("BofA: extracted last4 = {}", last4M.group(1));
            }
        }

        log.info("Account APR after extraction — apr={}, promoApr={}, promoEnd={}",
            account.getApr(), account.getPromoApr(), account.getPromoAprEndDate());
    }

    @Override
    public List<Transaction> parse(String pdfText, Statement statement, Account account) {
        log.info("Using BofA-specific parser");

        LocalDate[] period = detectStatementPeriod(pdfText, statement);
        LocalDate periodStart = period[0];
        LocalDate periodEnd   = period[1];
        log.info("Detected statement period: {} to {}", periodStart, periodEnd);

        List<Transaction> transactions = new ArrayList<>();
        String[] lines = pdfText.split("\n");

        Transaction.TransactionType currentSection = Transaction.TransactionType.DEBIT;
        boolean inTransactionSection = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;

            // ── Section detection ──────────────────────────────────────────────
            // Only treat as section header if the line does NOT start with MM/dd
            // (a line starting with a date is a transaction, not a section header)
            boolean lineStartsWithDate = line.matches("^\\d{2}/\\d{2}.*");
            if (!lineStartsWithDate) {
                String lower = line.toLowerCase();
                if (lower.matches(".*\\bpurchases\\b.*") && !lower.contains("total")) {
                    currentSection = Transaction.TransactionType.DEBIT;
                    inTransactionSection = true;
                    continue;
                }
                if (lower.matches(".*\\b(payments?|credits?)\\b.*") && !lower.contains("total")) {
                    currentSection = Transaction.TransactionType.CREDIT;
                    inTransactionSection = true;
                    continue;
                }
                if (lower.matches(".*\\bfees?\\b.*") && !lower.contains("total")
                        && !lower.contains("no fee") && !lower.contains("annual fee")) {
                    currentSection = Transaction.TransactionType.FEE;
                    inTransactionSection = true;
                    continue;
                }
                if (lower.matches(".*\\binterest\\s+charged\\b.*") && !lower.matches(".*\\d{2}/\\d{2}.*")) {
                    currentSection = Transaction.TransactionType.INTEREST;
                    inTransactionSection = true;
                    continue;
                }
            }

            // ── Try two-date format: MM/dd  MM/dd  Description  Amount ─────────
            Matcher m2 = BOFA_TWO_DATE.matcher(line);
            if (m2.matches()) {
                Transaction t = buildTransaction(
                    m2.group(1), m2.group(2), m2.group(3), m2.group(4),
                    periodStart, periodEnd, currentSection, statement, account
                );
                if (t != null) { transactions.add(t); inTransactionSection = true; }
                continue;
            }

            // ── Try single-date format: MM/dd  Description  Amount ────────────
            Matcher m1 = BOFA_ONE_DATE.matcher(line);
            if (m1.matches() && inTransactionSection) {
                Transaction t = buildTransaction(
                    m1.group(1), null, m1.group(2), m1.group(3),
                    periodStart, periodEnd, currentSection, statement, account
                );
                if (t != null) transactions.add(t);
            }
        }

        // Always extract payment summary and YTD totals — even if falling back to generic
        extractPaymentSummary(pdfText, statement);
        extractYtdTotals(pdfText, statement);

        // Belt-and-suspenders: remove any $0 transactions that slipped through
        int beforeFilter = transactions.size();
        transactions.removeIf(t -> t.getAmount().compareTo(java.math.BigDecimal.ZERO) == 0);
        if (transactions.size() < beforeFilter) {
            log.info("Removed {} zero-amount transactions", beforeFilter - transactions.size());
        }

        if (transactions.isEmpty()) {
            log.warn("BofA two-date parser found 0 transactions — falling back to generic parser");
            return super.parse(pdfText, statement, account);
        }

        log.info("BofA parser extracted {} transactions", transactions.size());
        return transactions;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void extractPaymentSummary(String pdfText, Statement statement) {
        if (statement == null) return;

        Matcher minM = MIN_PAYMENT.matcher(pdfText);
        if (minM.find()) {
            try {
                statement.setMinimumPayment(parseAmount(minM.group(1)));
                log.info("Extracted minimum payment: {}", minM.group(1));
            } catch (Exception e) {
                log.debug("Could not parse minimum payment: {}", e.getMessage());
            }
        }

        Matcher dueM = PAYMENT_DUE_DATE.matcher(pdfText);
        if (dueM.find()) {
            LocalDate d = parseFullDate(dueM.group(1));
            if (d != null) {
                statement.setPaymentDueDate(d);
                log.info("Extracted payment due date: {}", d);
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
                log.info("Extracted YTD fees ({}): {}", feesM.group(1), feesM.group(2));
            } catch (Exception e) {
                log.debug("Could not parse YTD fees: {}", e.getMessage());
            }
        }

        Matcher intM = YTD_INTEREST.matcher(pdfText);
        if (intM.find()) {
            try {
                statement.setYtdTotalInterest(parseAmount(intM.group(2)));
                if (statement.getYtdYear() == null) {
                    statement.setYtdYear(Integer.parseInt(intM.group(1)));
                }
                log.info("Extracted YTD interest ({}): {}", intM.group(1), intM.group(2));
            } catch (Exception e) {
                log.debug("Could not parse YTD interest: {}", e.getMessage());
            }
        }
    }

    private Transaction buildTransaction(
        String transDateStr, String postDateStr, String rawDesc, String amountStr,
        LocalDate periodStart, LocalDate periodEnd,
        Transaction.TransactionType sectionType,
        Statement statement, Account account
    ) {
        try {
            // Clean description
            String description = REF_NUMBER.matcher(rawDesc.trim()).replaceAll("").trim();

            // Strip leading post-date that OCR bleeds into description: "02/08 INTEREST..."
            description = LEADING_DATE.matcher(description).replaceFirst("").trim();

            if (isHeaderOrSkipLine(description)) return null;

            // Skip $0.00 transactions (BofA "no interest charged" informational lines)
            BigDecimal amount = parseAmount(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                log.debug("Skipping $0.00 transaction: {}", description);
                return null;
            }

            LocalDate transDate = resolveBofaDate(transDateStr, periodStart, periodEnd);
            if (transDate == null) return null;

            LocalDate postDate = postDateStr != null
                ? resolveBofaDate(postDateStr, periodStart, periodEnd)
                : null;

            boolean isCredit = amount.compareTo(BigDecimal.ZERO) < 0
                || description.toLowerCase().contains("payment")
                || description.toLowerCase().contains("credit")
                || sectionType == Transaction.TransactionType.CREDIT;

            Transaction.TransactionType type;
            if (sectionType == Transaction.TransactionType.FEE) {
                type = Transaction.TransactionType.FEE;
            } else if (sectionType == Transaction.TransactionType.INTEREST) {
                type = Transaction.TransactionType.INTEREST;
            } else {
                type = isCredit ? Transaction.TransactionType.CREDIT : Transaction.TransactionType.DEBIT;
            }

            String merchantName = normalizeMerchant(description);
            String category = categorize(merchantName, type);

            return Transaction.builder()
                .account(account)
                .statement(statement)
                .transactionDate(transDate)
                .postDate(postDate)
                .description(description)
                .merchantName(merchantName)
                .amount(amount.abs())
                .type(type)
                .category(category)
                .build();

        } catch (Exception e) {
            log.debug("Skipping line — parse error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * BofA-specific merchant normalization.
     * Strips: UPC codes, trailing card digit segments, city/state location suffixes.
     * Example: "WHOLEFDS UPC#10644 CHARLOTTE NC 0317 3266" → "WHOLEFDS"
     * Example: "PATEL BROTHERS CHARLOT CHARLOTTE NC 2559 3266" → "PATEL BROTHERS"
     * Example: "NETFLIX.COM CA" → "NETFLIX.COM"
     */
    @Override
    protected String normalizeMerchant(String description) {
        if (description == null) return null;
        String name = description.trim();

        // Strip leading post-date prefix if it survived (shouldn't, but just in case)
        name = LEADING_DATE.matcher(name).replaceFirst("").trim();

        // Strip UPC codes: "UPC#12345" or "UPC 12345"
        name = UPC_CODE.matcher(name).replaceAll("").trim();

        // Strip trailing reference numbers (15+ digits)
        name = REF_NUMBER.matcher(name).replaceAll("").trim();

        // Strip trailing card segments (1-2 groups of 4 digits): " 0317 3266"
        name = TRAILING_CARD_DIGITS.matcher(name).replaceAll("").trim();

        // Strip trailing location suffix: "CHARLOTTE NC" or "CA"
        // But be conservative — only strip if the remaining name has content
        String stripped = TRAILING_LOCATION.matcher(name).replaceAll("").trim();
        if (stripped.length() >= 2) {
            name = stripped;
        }

        // Remove extra whitespace
        name = name.replaceAll("\\s+", " ").trim();

        // Fall back to original if cleanup removed everything
        return name.isEmpty() ? description.trim() : name;
    }

    /**
     * Resolve a MM/dd date string into a full LocalDate using the statement period.
     */
    private LocalDate resolveBofaDate(String mmdd, LocalDate periodStart, LocalDate periodEnd) {
        if (mmdd == null) return null;
        int year = periodEnd != null ? periodEnd.getYear() : LocalDate.now().getYear();

        LocalDate candidate = parseMmDd(mmdd, year);
        if (candidate == null) return null;

        // Handle year boundary (Dec transactions on a Jan statement)
        if (periodStart != null && periodEnd != null) {
            if (candidate.isAfter(periodEnd)) {
                LocalDate prev = parseMmDd(mmdd, year - 1);
                if (prev != null && !prev.isBefore(periodStart)) return prev;
            }
        }
        return candidate;
    }

    private LocalDate parseMmDd(String mmdd, int year) {
        try {
            return LocalDate.parse(mmdd + "/" + year, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate[] detectStatementPeriod(String text, Statement statement) {
        Matcher m = OPENING_CLOSING.matcher(text);
        if (m.find()) {
            LocalDate start = parseFullDate(m.group(1));
            LocalDate end   = parseFullDate(m.group(2));
            if (start != null && end != null) return new LocalDate[]{start, end};
        }

        m = CLOSING_DATE.matcher(text);
        if (m.find()) {
            LocalDate end = parseFullDate(m.group(1));
            if (end != null) return new LocalDate[]{end.minusDays(30), end};
        }

        m = PERIOD_RANGE.matcher(text);
        if (m.find()) {
            LocalDate start = parseFullDate(m.group(1));
            LocalDate end   = parseFullDate(m.group(2));
            if (start != null && end != null) return new LocalDate[]{start, end};
        }

        if (statement.getStartDate() != null && statement.getEndDate() != null) {
            return new LocalDate[]{statement.getStartDate(), statement.getEndDate()};
        }

        LocalDate now = LocalDate.now();
        return new LocalDate[]{now.withDayOfMonth(1), now};
    }

    private LocalDate parseFullDate(String dateStr) {
        String[] patterns = {"MM/dd/yyyy", "M/d/yyyy", "MM/dd/yy", "M/d/yy"};
        for (String p : patterns) {
            try {
                LocalDate d = LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern(p));
                if (d.getYear() < 100) d = d.withYear(d.getYear() + 2000);
                return d;
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private boolean isHeaderOrSkipLine(String description) {
        String d = description.toLowerCase();
        return d.equals("description") || d.equals("date") || d.equals("trans date")
            || d.equals("post date") || d.equals("reference") || d.equals("amount")
            || d.startsWith("account number") || d.startsWith("trans  post")
            || d.startsWith("please see") || d.startsWith("continued")
            || d.startsWith("activity description") || d.startsWith("reference number");
    }
}

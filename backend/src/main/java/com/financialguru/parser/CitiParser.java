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
 * Citi credit card statement parser.
 *
 * Citi PDF format (PDFBox text extraction):
 *   Trans.  Post
 *   Date    Date    Description                                 Amount
 *   01/15   01/16   AMAZON.COM*1A2B3C4D SEATTLE WA              48.25
 *   01/12   01/14   PAYMENT THANK YOU                          -500.00
 *
 * Amount: no $ prefix, negative = payment/credit
 * Dates: MM/DD (no year — inferred from statement period)
 */
@Component
@Slf4j
public class CitiParser extends GenericPdfParser {

    // Two-date format: MM/DD  MM/DD  description  amount
    private static final Pattern CITI_TXN_2DATE = Pattern.compile(
        "^(\\d{2}/\\d{2})\\s+(\\d{2}/\\d{2})\\s+(.+?)\\s{2,}(-?[\\d,]+\\.\\d{2})\\s*$"
    );
    // Fallback single-date: MM/DD  description  amount
    private static final Pattern CITI_TXN_1DATE = Pattern.compile(
        "^(\\d{2}/\\d{2})\\s{2,}(.+?)\\s{2,}(-?[\\d,]+\\.\\d{2})\\s*$"
    );
    // Loose fallback
    private static final Pattern CITI_TXN_LOOSE = Pattern.compile(
        "^(\\d{2}/\\d{2})\\s+(.+?)\\s+(-?[\\d,]+\\.\\d{2})\\s*$"
    );

    // ── Statement period ──────────────────────────────────────────────────────
    // "Statement Period  MM/DD/YYYY to MM/DD/YYYY"
    private static final Pattern PERIOD_TO = Pattern.compile(
        "(?i)(?:statement\\s+period|billing\\s+period)[:\\s]+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+(?:to|through|[-–])\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );
    // "01/16/2025 through 02/15/2025"
    private static final Pattern PERIOD_DATES_ONLY = Pattern.compile(
        "(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+(?:through|to)\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── Account info ──────────────────────────────────────────────────────────
    // "Account number ending in 1234" or "Card ending in 1234"
    private static final Pattern ACCOUNT_LAST4 = Pattern.compile(
        "(?i)(?:account\\s+(?:number\\s+)?ending\\s+in|card\\s+ending(?:\\s+in)?)\\s+(\\d{4})"
    );
    // "New Balance  $1,234.56" or "Total New Balance  $1,234.56"
    private static final Pattern NEW_BALANCE = Pattern.compile(
        "(?im)^(?:total\\s+)?new\\s+balance\\s+\\$?([\\d,]+\\.\\d{2})\\s*$"
    );
    // "Credit Line  $5,000" or "Credit Limit  $5,000"
    private static final Pattern CREDIT_LIMIT = Pattern.compile(
        "(?i)(?:credit\\s+line|credit\\s+limit)\\s+\\$?([\\d,]+(?:\\.\\d{2})?)"
    );
    // "Available Credit  $3,765.44"
    private static final Pattern AVAILABLE_CREDIT = Pattern.compile(
        "(?i)available\\s+credit\\s+\\$?([\\d,]+(?:\\.\\d{2})?)"
    );

    // ── Payment summary ───────────────────────────────────────────────────────
    // "Minimum Payment Due  $25.00"
    private static final Pattern MIN_PAYMENT = Pattern.compile(
        "(?i)minimum\\s+payment\\s+due\\s+\\$?([\\d,]+\\.\\d{2})"
    );
    // "Payment Due Date  01/15/2026"
    private static final Pattern PAYMENT_DUE_DATE = Pattern.compile(
        "(?i)payment\\s+due\\s+date[:\\s]+(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── APR ───────────────────────────────────────────────────────────────────
    // "Variable APR  28.49%"  or  "Purchase APR  28.49%"
    private static final Pattern PURCHASE_APR = Pattern.compile(
        "(?i)(?:variable\\s+|purchase\\s+)?(?:purchase\\s+)?apr[:\\s]+(\\d{1,2}\\.\\d{2})%"
    );
    // Promo: "0.00% through MM/DD/YYYY" or "Promotional APR  0.00%  Expires MM/DD/YYYY"
    private static final Pattern PROMO_APR = Pattern.compile(
        "(?i)(?:promotional\\s+|intro(?:ductory)?\\s+)?apr\\s+(\\d{1,2}\\.\\d{2})%[^\\n]*(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── YTD totals ────────────────────────────────────────────────────────────
    // "Total Fees Charged in 2026  $0.00"
    private static final Pattern YTD_FEES = Pattern.compile(
        "(?i)total\\s+fees\\s+charged\\s+in\\s+(\\d{4})\\s+\\$?([\\d,]+\\.\\d{2})"
    );
    // "Total Interest Charged in 2026  $72.00"
    private static final Pattern YTD_INTEREST = Pattern.compile(
        "(?i)total\\s+interest\\s+charged\\s+in\\s+(\\d{4})\\s+\\$?([\\d,]+\\.\\d{2})"
    );

    @Override
    public boolean supports(String institution) {
        return "CITI".equals(institution);
    }

    @Override
    public void extractAccountInfo(String pdfText, Account account) {
        if (account == null || pdfText == null) return;

        account.setType(Account.AccountType.CREDIT_CARD);

        // Last 4
        if (account.getLast4() == null) {
            Matcher m = ACCOUNT_LAST4.matcher(pdfText);
            if (m.find()) {
                account.setLast4(m.group(1));
                log.info("Citi: last4 = {}", m.group(1));
            }
        }

        // Current balance (always update from latest statement)
        Matcher balM = NEW_BALANCE.matcher(pdfText);
        if (balM.find()) {
            try {
                account.setCurrentBalance(parseAmount(balM.group(1)));
                log.info("Citi: current balance = {}", balM.group(1));
            } catch (Exception e) {
                log.debug("Citi: could not parse new balance");
            }
        }

        // Credit limit
        if (account.getCreditLimit() == null) {
            Matcher m = CREDIT_LIMIT.matcher(pdfText);
            if (m.find()) {
                try {
                    String val = m.group(1);
                    if (!val.contains(".")) val = val + ".00";
                    account.setCreditLimit(parseAmount(val));
                    log.info("Citi: credit limit = {}", val);
                } catch (Exception e) {
                    log.debug("Citi: could not parse credit limit");
                }
            }
        }

        // Available credit
        if (account.getAvailableCredit() == null) {
            Matcher m = AVAILABLE_CREDIT.matcher(pdfText);
            if (m.find()) {
                try {
                    String val = m.group(1);
                    if (!val.contains(".")) val = val + ".00";
                    account.setAvailableCredit(parseAmount(val));
                    log.info("Citi: available credit = {}", val);
                } catch (Exception e) {
                    log.debug("Citi: could not parse available credit");
                }
            }
        }

        // Promo APR first (lower rate with expiry date)
        if (account.getPromoApr() == null) {
            Matcher m = PROMO_APR.matcher(pdfText);
            if (m.find()) {
                try {
                    BigDecimal promo = new BigDecimal(m.group(1));
                    if (promo.compareTo(BigDecimal.valueOf(10)) < 0) {
                        account.setPromoApr(promo);
                        LocalDate expiry = parseShortDate(m.group(2));
                        if (expiry != null) account.setPromoAprEndDate(expiry);
                        log.info("Citi: promo APR = {}% expires {}", promo, expiry);
                    }
                } catch (Exception e) {
                    log.debug("Citi: could not parse promo APR");
                }
            }
        }

        // Regular APR
        if (account.getApr() == null) {
            Matcher m = PURCHASE_APR.matcher(pdfText);
            BigDecimal lastApr = null;
            while (m.find()) {
                try {
                    BigDecimal candidate = new BigDecimal(m.group(1));
                    if (candidate.compareTo(BigDecimal.valueOf(5)) > 0) {
                        lastApr = candidate;
                    }
                } catch (Exception ignored) {}
            }
            if (lastApr != null) {
                account.setApr(lastApr);
                log.info("Citi: APR = {}%", lastApr);
            }
        }
    }

    @Override
    public List<Transaction> parse(String pdfText, Statement statement, Account account) {
        log.info("Using Citi parser");

        extractPaymentSummary(pdfText, statement);
        extractYtdTotals(pdfText, statement);

        LocalDate[] period = detectPeriod(pdfText, statement);
        LocalDate periodStart = period[0];
        LocalDate periodEnd   = period[1];
        log.info("Citi statement period: {} to {}", periodStart, periodEnd);

        List<Transaction> transactions = new ArrayList<>();
        boolean inTransactionSection = false;
        String[] lines = pdfText.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) continue;

            String lower = line.toLowerCase();

            // Section markers
            if (lower.contains("purchases and adjustments") || lower.contains("standard purchases") ||
                lower.contains("account activity") || lower.contains("transaction detail") ||
                lower.contains("new charges") || lower.contains("payments and credits")) {
                inTransactionSection = true;
                continue;
            }
            if (lower.startsWith("total purchases") || lower.startsWith("total payments") ||
                lower.startsWith("fees charged") || lower.startsWith("interest charged") ||
                lower.startsWith("2026") || lower.startsWith("2025") ||
                lower.startsWith("total fees") || lower.startsWith("total interest")) {
                inTransactionSection = false;
            }

            if (!inTransactionSection) {
                // Still try to parse even outside known sections
                // (Citi sometimes lacks clear section headers)
            }

            // Try two-date pattern first
            Matcher m = CITI_TXN_2DATE.matcher(line);
            String dateStr, desc, amtStr;
            if (m.matches()) {
                dateStr = m.group(1);
                desc    = m.group(3).trim();
                amtStr  = m.group(4);
            } else {
                m = CITI_TXN_1DATE.matcher(line);
                if (!m.matches()) {
                    m = CITI_TXN_LOOSE.matcher(line);
                    if (!m.matches()) continue;
                }
                dateStr = m.group(1);
                desc    = m.group(2).trim();
                amtStr  = m.group(3);
            }

            try {
                if (desc.equalsIgnoreCase("description") || desc.equalsIgnoreCase("amount")) continue;
                // Skip subtotal / summary lines
                if (desc.toLowerCase().startsWith("total ") || desc.toLowerCase().startsWith("new balance")) continue;

                LocalDate date = resolveDate(dateStr, periodStart, periodEnd);
                if (date == null) continue;

                BigDecimal amount = parseAmount(amtStr);

                Transaction.TransactionType type;
                String descUpper = desc.toUpperCase();
                if (descUpper.contains("PAYMENT") || descUpper.contains("AUTOPAY") ||
                    descUpper.contains("CREDIT ADJUSTMENT") || amount.compareTo(BigDecimal.ZERO) < 0) {
                    type = Transaction.TransactionType.CREDIT;
                } else if (lower.contains("interest charge") || lower.contains("interest charged")) {
                    type = Transaction.TransactionType.INTEREST;
                } else if (lower.contains("fee") || lower.contains("annual fee") || lower.contains("late fee")) {
                    type = Transaction.TransactionType.FEE;
                } else {
                    type = Transaction.TransactionType.DEBIT;
                }

                String merchant = normalizeMerchant(desc);
                String category = categorize(merchant, type);

                transactions.add(Transaction.builder()
                    .account(account)
                    .statement(statement)
                    .transactionDate(date)
                    .description(desc)
                    .merchantName(merchant)
                    .amount(amount.abs())
                    .type(type)
                    .category(category)
                    .build());

                log.debug("Citi parsed: {} | {} | {}", date, merchant, amount);
            } catch (Exception e) {
                log.debug("Citi: could not parse line: {} — {}", line, e.getMessage());
            }
        }

        log.info("Citi parser extracted {} transactions", transactions.size());

        if (transactions.isEmpty()) {
            log.warn("Citi parser found 0 transactions — falling back to generic");
            return super.parse(pdfText, statement, account);
        }
        return transactions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void extractPaymentSummary(String pdfText, Statement statement) {
        if (statement == null) return;

        Matcher minM = MIN_PAYMENT.matcher(pdfText);
        if (minM.find()) {
            try {
                statement.setMinimumPayment(parseAmount(minM.group(1)));
                log.info("Citi: minimum payment = {}", minM.group(1));
            } catch (Exception e) {
                log.debug("Citi: could not parse minimum payment");
            }
        }

        Matcher dueM = PAYMENT_DUE_DATE.matcher(pdfText);
        if (dueM.find()) {
            LocalDate d = parseShortDate(dueM.group(1));
            if (d != null) {
                statement.setPaymentDueDate(d);
                log.info("Citi: payment due date = {}", d);
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
                log.info("Citi: YTD fees ({}) = {}", feesM.group(1), feesM.group(2));
            } catch (Exception e) {
                log.debug("Citi: could not parse YTD fees");
            }
        }

        Matcher intM = YTD_INTEREST.matcher(pdfText);
        if (intM.find()) {
            try {
                statement.setYtdTotalInterest(parseAmount(intM.group(2)));
                if (statement.getYtdYear() == null) {
                    statement.setYtdYear(Integer.parseInt(intM.group(1)));
                }
                log.info("Citi: YTD interest ({}) = {}", intM.group(1), intM.group(2));
            } catch (Exception e) {
                log.debug("Citi: could not parse YTD interest");
            }
        }
    }

    private LocalDate[] detectPeriod(String text, Statement statement) {
        // Try "Statement Period MM/DD/YYYY to MM/DD/YYYY"
        Matcher m = PERIOD_TO.matcher(text);
        if (m.find()) {
            LocalDate start = parseShortDate(m.group(1));
            LocalDate end   = parseShortDate(m.group(2));
            if (start != null && end != null) return new LocalDate[]{start, end};
        }
        // Try bare "MM/DD/YYYY through MM/DD/YYYY"
        m = PERIOD_DATES_ONLY.matcher(text);
        if (m.find()) {
            LocalDate start = parseShortDate(m.group(1));
            LocalDate end   = parseShortDate(m.group(2));
            if (start != null && end != null) return new LocalDate[]{start, end};
        }
        if (statement.getStartDate() != null && statement.getEndDate() != null) {
            return new LocalDate[]{statement.getStartDate(), statement.getEndDate()};
        }
        LocalDate now = LocalDate.now();
        return new LocalDate[]{now.minusDays(30), now};
    }

    private LocalDate resolveDate(String mmdd, LocalDate start, LocalDate end) {
        int year = end != null ? end.getYear() : LocalDate.now().getYear();
        LocalDate candidate = parseMmDd(mmdd, year);
        if (candidate == null) return null;
        if (start != null && end != null && candidate.isAfter(end)) {
            LocalDate prev = parseMmDd(mmdd, year - 1);
            if (prev != null && !prev.isBefore(start)) return prev;
        }
        return candidate;
    }

    private LocalDate parseMmDd(String mmdd, int year) {
        try {
            return LocalDate.parse(mmdd + "/" + year, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        } catch (DateTimeParseException e) { return null; }
    }

    private LocalDate parseShortDate(String dateStr) {
        if (dateStr == null) return null;
        String[] patterns = {"MM/dd/yy", "MM/dd/yyyy", "M/d/yy", "M/d/yyyy"};
        for (String p : patterns) {
            try {
                LocalDate d = LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern(p));
                if (d.getYear() < 100) d = d.withYear(d.getYear() + 2000);
                return d;
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}

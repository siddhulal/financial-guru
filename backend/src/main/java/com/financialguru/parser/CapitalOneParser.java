package com.financialguru.parser;

import com.financialguru.model.Account;
import com.financialguru.model.Statement;
import com.financialguru.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Capital One credit card statement parser.
 *
 * Capital One PDF format:
 *   Jan. 15, 2026  AMAZON.COM*1A2B3C4D  Seattle WA         $48.25
 *   Jan. 12, 2026  PAYMENT THANK YOU                      -$500.00
 *
 * Dates: "Jan. 15, 2026" or "01/15/2026"
 * Amount: $XX.XX prefix, negative = payment/credit
 */
@Component
@Slf4j
public class CapitalOneParser extends GenericPdfParser {

    // ── NEW: "Feb 14 Feb 14 AMAZON MKTPL*FY3737CG3 SEATTLEWA $10.70"
    // ──      "Feb 14 Feb 14 CAPITAL ONE AUTOPAY PYMT - $63.00"
    // Two short dates (no year), description, optional "- " prefix on amount
    private static final Pattern CAP1_TXN_TWO_DATES = Pattern.compile(
        "^([A-Z][a-z]{2}\\s+\\d{1,2})\\s+[A-Z][a-z]{2}\\s+\\d{1,2}\\s+(.+?)\\s+(-\\s*\\$[\\d,]+\\.\\d{2}|\\$[\\d,]+\\.\\d{2})\\s*$"
    );

    // Primary: "Jan. 15, 2026  description  $amount"
    private static final Pattern CAP1_TXN_FULL = Pattern.compile(
        "^([A-Z][a-z]{2}\\.?\\s+\\d{1,2},\\s+\\d{4})\\s{2,}(.+?)\\s{2,}(-?\\$?[\\d,]+\\.\\d{2})\\s*$"
    );
    // Fallback: "01/15/2026  description  $amount"
    private static final Pattern CAP1_TXN_DATE = Pattern.compile(
        "^(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s{2,}(.+?)\\s{2,}(-?\\$?[\\d,]+\\.\\d{2})\\s*$"
    );
    // Loose fallback
    private static final Pattern CAP1_TXN_LOOSE = Pattern.compile(
        "^([A-Z][a-z]{2}\\.?\\s+\\d{1,2},\\s+\\d{4})\\s+(.+?)\\s+(-?\\$?[\\d,]+\\.\\d{2})\\s*$"
    );

    // Date formatters for Capital One style dates
    private static final List<DateTimeFormatter> CAP1_DATE_FMTS = List.of(
        DateTimeFormatter.ofPattern("MMM. d, yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMM d, yyyy",  Locale.US),
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yy")
    );

    // Short date (no year): "Feb 14" → need to combine with statement year
    private static final DateTimeFormatter SHORT_DATE_FMT =
        DateTimeFormatter.ofPattern("MMM d", Locale.US);

    // ── Statement period ──────────────────────────────────────────────────────
    // "BILLING PERIOD  MM/DD/YY - MM/DD/YY" or "Statement Period: Jan 1, 2026 - Jan 31, 2026"
    private static final Pattern PERIOD_SLASH = Pattern.compile(
        "(?i)(?:billing\\s+period|statement\\s+period)[:\\s]+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s*[-–]\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );
    private static final Pattern PERIOD_MONTH = Pattern.compile(
        "(?i)(?:billing\\s+period|statement\\s+period)[:\\s]+([A-Z][a-z]{2}\\.?\\s+\\d{1,2},\\s+\\d{4})\\s*[-–]\\s*([A-Z][a-z]{2}\\.?\\s+\\d{1,2},\\s+\\d{4})"
    );
    // NEW: header-style period "Jan 21, 2026 - Feb 17, 2026   |  28 days in Billing Cycle"
    private static final Pattern PERIOD_HEADER = Pattern.compile(
        "([A-Z][a-z]{2}\\s+\\d{1,2},\\s+\\d{4})\\s*[-–]\\s*([A-Z][a-z]{2}\\s+\\d{1,2},\\s+\\d{4})\\s*\\|"
    );

    // ── Account info ──────────────────────────────────────────────────────────
    // "Account ending in 1234" or "Card ending 1234"
    private static final Pattern ACCOUNT_LAST4 = Pattern.compile(
        "(?i)(?:account|card)\\s+ending\\s+(?:in\\s+)?(\\d{4})"
    );
    // "New Balance  $1,234.56"
    private static final Pattern NEW_BALANCE = Pattern.compile(
        "(?im)^new\\s+balance\\s+\\$?([\\d,]+\\.\\d{2})\\s*$"
    );
    // "Credit Limit  $5,000" or "Credit Limit:  $5,000.00"
    private static final Pattern CREDIT_LIMIT = Pattern.compile(
        "(?i)credit\\s+limit[:\\s]+\\$?([\\d,]+(?:\\.\\d{2})?)"
    );
    // "Available Credit  $3,765.44"
    private static final Pattern AVAILABLE_CREDIT = Pattern.compile(
        "(?i)available\\s+credit[:\\s]+\\$?([\\d,]+(?:\\.\\d{2})?)"
    );

    // ── Payment summary ───────────────────────────────────────────────────────
    private static final Pattern MIN_PAYMENT = Pattern.compile(
        "(?i)minimum\\s+payment\\s+due[:\\s]+\\$?([\\d,]+\\.\\d{2})"
    );
    private static final Pattern PAYMENT_DUE_DATE = Pattern.compile(
        "(?i)payment\\s+due\\s+(?:date)?[:\\s]+(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── APR ───────────────────────────────────────────────────────────────────
    // "Variable Purchase APR  28.49%"  or  "Purchase APR  28.49%"
    private static final Pattern PURCHASE_APR = Pattern.compile(
        "(?i)(?:variable\\s+)?purchase\\s+apr[:\\s]+(\\d{1,2}\\.\\d{2})%"
    );
    // Promo: "0.00% intro APR  through  MM/DD/YYYY"
    private static final Pattern PROMO_APR = Pattern.compile(
        "(?i)(\\d{1,2}\\.\\d{2})%\\s+intro(?:ductory)?\\s+apr[^\\n]*(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── YTD totals ────────────────────────────────────────────────────────────
    private static final Pattern YTD_FEES = Pattern.compile(
        "(?i)(?:total\\s+)?fees\\s+(?:charged\\s+)?in\\s+(\\d{4})[:\\s]+\\$?([\\d,]+\\.\\d{2})"
    );
    private static final Pattern YTD_INTEREST = Pattern.compile(
        "(?i)(?:total\\s+)?interest\\s+(?:charged\\s+)?in\\s+(\\d{4})[:\\s]+\\$?([\\d,]+\\.\\d{2})"
    );

    @Override
    public boolean supports(String institution) {
        return "CAPITAL_ONE".equals(institution);
    }

    @Override
    public void extractAccountInfo(String pdfText, Account account) {
        if (account == null || pdfText == null) return;

        account.setType(Account.AccountType.CREDIT_CARD);

        if (account.getLast4() == null) {
            Matcher m = ACCOUNT_LAST4.matcher(pdfText);
            if (m.find()) {
                account.setLast4(m.group(1));
                log.info("CapitalOne: last4 = {}", m.group(1));
            }
        }

        Matcher balM = NEW_BALANCE.matcher(pdfText);
        if (balM.find()) {
            try {
                account.setCurrentBalance(parseAmount(balM.group(1)));
                log.info("CapitalOne: current balance = {}", balM.group(1));
            } catch (Exception e) {
                log.debug("CapitalOne: could not parse new balance");
            }
        }

        if (account.getCreditLimit() == null) {
            Matcher m = CREDIT_LIMIT.matcher(pdfText);
            if (m.find()) {
                try {
                    String val = m.group(1);
                    if (!val.contains(".")) val = val + ".00";
                    account.setCreditLimit(parseAmount(val));
                    log.info("CapitalOne: credit limit = {}", val);
                } catch (Exception e) {
                    log.debug("CapitalOne: could not parse credit limit");
                }
            }
        }

        if (account.getAvailableCredit() == null) {
            Matcher m = AVAILABLE_CREDIT.matcher(pdfText);
            if (m.find()) {
                try {
                    String val = m.group(1);
                    if (!val.contains(".")) val = val + ".00";
                    account.setAvailableCredit(parseAmount(val));
                    log.info("CapitalOne: available credit = {}", val);
                } catch (Exception e) {
                    log.debug("CapitalOne: could not parse available credit");
                }
            }
        }

        // Promo APR
        if (account.getPromoApr() == null) {
            Matcher m = PROMO_APR.matcher(pdfText);
            if (m.find()) {
                try {
                    BigDecimal promo = new BigDecimal(m.group(1));
                    if (promo.compareTo(BigDecimal.valueOf(10)) < 0) {
                        account.setPromoApr(promo);
                        LocalDate expiry = parseCap1Date(m.group(2));
                        if (expiry != null) account.setPromoAprEndDate(expiry);
                        log.info("CapitalOne: promo APR = {}% expires {}", promo, expiry);
                    }
                } catch (Exception e) {
                    log.debug("CapitalOne: could not parse promo APR");
                }
            }
        }

        if (account.getApr() == null) {
            Matcher m = PURCHASE_APR.matcher(pdfText);
            if (m.find()) {
                try {
                    account.setApr(new BigDecimal(m.group(1)));
                    log.info("CapitalOne: APR = {}%", m.group(1));
                } catch (Exception e) {
                    log.debug("CapitalOne: could not parse APR");
                }
            }
        }
    }

    @Override
    public List<Transaction> parse(String pdfText, Statement statement, Account account) {
        log.info("Using Capital One parser");

        extractPaymentSummary(pdfText, statement);
        extractYtdTotals(pdfText, statement);

        LocalDate[] period = detectPeriod(pdfText, statement);
        log.info("CapitalOne statement period: {} to {}", period[0], period[1]);

        List<Transaction> transactions = new ArrayList<>();
        String[] lines = pdfText.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) continue;

            // Skip obvious header/footer lines
            String lower = line.toLowerCase();
            if (lower.startsWith("trans date") || lower.startsWith("post date") ||
                lower.startsWith("date") || lower.startsWith("description") ||
                lower.contains("page ") || lower.contains("continued") ||
                lower.contains("total fees") || lower.contains("total interest") ||
                lower.contains("interest charge on") || lower.contains("year-to-date") ||
                lower.contains("total transactions")) continue;

            Matcher m;
            String dateStr, desc, amtStr;
            boolean shortDate = false;

            // 1. NEW two-date format: "Feb 14 Feb 14 AMAZON MKTPL $10.70"
            m = CAP1_TXN_TWO_DATES.matcher(line);
            if (m.matches()) {
                dateStr   = m.group(1); // trans date only, e.g. "Feb 14"
                desc      = m.group(2).trim();
                amtStr    = m.group(3);
                shortDate = true;
            }
            // 2. Full date with year: "Jan. 15, 2026  description  $amount"
            else {
                m = CAP1_TXN_FULL.matcher(line);
                if (m.matches()) {
                    dateStr = m.group(1);
                    desc    = m.group(2).trim();
                    amtStr  = m.group(3);
                } else {
                    // 3. Slash date: "01/15/2026  description  $amount"
                    m = CAP1_TXN_DATE.matcher(line);
                    if (m.matches()) {
                        dateStr = m.group(1);
                        desc    = m.group(2).trim();
                        amtStr  = m.group(3);
                    } else {
                        // 4. Loose fallback
                        m = CAP1_TXN_LOOSE.matcher(line);
                        if (!m.matches()) continue;
                        dateStr = m.group(1);
                        desc    = m.group(2).trim();
                        amtStr  = m.group(3);
                    }
                }
            }

            try {
                if (desc.equalsIgnoreCase("description") || desc.equalsIgnoreCase("amount")) continue;
                if (desc.toLowerCase().startsWith("total ") || desc.toLowerCase().startsWith("new balance")) continue;

                // Parse date — short dates need year from billing period
                LocalDate date;
                if (shortDate) {
                    date = parseShortDate(dateStr, period[1]);
                } else {
                    date = parseCap1Date(dateStr);
                }
                if (date == null) continue;

                // Normalize amount: "- $63.00" → "-63.00",  "$10.70" → "10.70"
                String normalizedAmt = amtStr.replace("$", "").replace(" ", "").trim();
                BigDecimal amount = parseAmount(normalizedAmt);

                Transaction.TransactionType type;
                String descUpper = desc.toUpperCase();
                String descLower = desc.toLowerCase();
                if (descUpper.contains("PAYMENT") || descUpper.contains("AUTOPAY") ||
                    descUpper.contains("CREDIT ADJUSTMENT") || descUpper.contains("REFUND") ||
                    amount.compareTo(BigDecimal.ZERO) < 0) {
                    type = Transaction.TransactionType.CREDIT;
                } else if (descLower.contains("interest charge")) {
                    type = Transaction.TransactionType.INTEREST;
                } else if (descLower.contains(" fee")) {
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

                log.debug("CapitalOne parsed: {} | {} | {}", date, merchant, amount);
            } catch (Exception e) {
                log.debug("CapitalOne: could not parse line: {} — {}", line, e.getMessage());
            }
        }

        log.info("Capital One parser extracted {} transactions", transactions.size());

        if (transactions.isEmpty()) {
            log.warn("Capital One parser found 0 transactions — falling back to generic");
            return super.parse(pdfText, statement, account);
        }
        return transactions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDate parseCap1Date(String dateStr) {
        if (dateStr == null) return null;
        for (DateTimeFormatter fmt : CAP1_DATE_FMTS) {
            try {
                LocalDate d = LocalDate.parse(dateStr.trim(), fmt);
                if (d.getYear() < 100) d = d.withYear(d.getYear() + 2000);
                return d;
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private LocalDate[] detectPeriod(String text, Statement statement) {
        // Try slash format first
        Matcher m = PERIOD_SLASH.matcher(text);
        if (m.find()) {
            LocalDate start = parseCap1Date(m.group(1));
            LocalDate end   = parseCap1Date(m.group(2));
            if (start != null && end != null) return new LocalDate[]{start, end};
        }
        // Try explicit "Billing Period:" label
        m = PERIOD_MONTH.matcher(text);
        if (m.find()) {
            LocalDate start = parseCap1Date(m.group(1));
            LocalDate end   = parseCap1Date(m.group(2));
            if (start != null && end != null) return new LocalDate[]{start, end};
        }
        // NEW: header-style "Jan 21, 2026 - Feb 17, 2026   |  28 days in Billing Cycle"
        m = PERIOD_HEADER.matcher(text);
        if (m.find()) {
            LocalDate start = parseCap1Date(m.group(1));
            LocalDate end   = parseCap1Date(m.group(2));
            if (start != null && end != null) {
                log.info("CapitalOne: detected period from header: {} to {}", start, end);
                return new LocalDate[]{start, end};
            }
        }
        if (statement.getStartDate() != null && statement.getEndDate() != null) {
            return new LocalDate[]{statement.getStartDate(), statement.getEndDate()};
        }
        LocalDate now = LocalDate.now();
        return new LocalDate[]{now.minusDays(30), now};
    }

    /**
     * Converts a short date like "Feb 14" to a full LocalDate by combining with the statement year.
     * Handles cross-year billing periods (e.g. Dec → Jan): if the short month is later in the
     * year than the period end month, the transaction belongs to the previous year.
     */
    private LocalDate parseShortDate(String shortDate, LocalDate periodEnd) {
        try {
            // Parse just month+day
            MonthDay md = MonthDay.parse(shortDate.trim(), SHORT_DATE_FMT);
            int year = periodEnd.getYear();
            // Cross-year: Dec transaction in a Jan-ending period → previous year
            if (md.getMonthValue() > periodEnd.getMonthValue() + 1) {
                year = year - 1;
            }
            return md.atYear(year);
        } catch (Exception e) {
            log.debug("CapitalOne: could not parse short date '{}': {}", shortDate, e.getMessage());
            return null;
        }
    }

    private void extractPaymentSummary(String pdfText, Statement statement) {
        if (statement == null) return;

        Matcher minM = MIN_PAYMENT.matcher(pdfText);
        if (minM.find()) {
            try {
                statement.setMinimumPayment(parseAmount(minM.group(1)));
                log.info("CapitalOne: minimum payment = {}", minM.group(1));
            } catch (Exception e) {
                log.debug("CapitalOne: could not parse minimum payment");
            }
        }

        Matcher dueM = PAYMENT_DUE_DATE.matcher(pdfText);
        if (dueM.find()) {
            LocalDate d = parseCap1Date(dueM.group(1));
            if (d != null) {
                statement.setPaymentDueDate(d);
                log.info("CapitalOne: payment due date = {}", d);
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
                log.info("CapitalOne: YTD fees ({}) = {}", feesM.group(1), feesM.group(2));
            } catch (Exception e) {
                log.debug("CapitalOne: could not parse YTD fees");
            }
        }

        Matcher intM = YTD_INTEREST.matcher(pdfText);
        if (intM.find()) {
            try {
                statement.setYtdTotalInterest(parseAmount(intM.group(2)));
                if (statement.getYtdYear() == null) {
                    statement.setYtdYear(Integer.parseInt(intM.group(1)));
                }
                log.info("CapitalOne: YTD interest ({}) = {}", intM.group(1), intM.group(2));
            } catch (Exception e) {
                log.debug("CapitalOne: could not parse YTD interest");
            }
        }
    }
}

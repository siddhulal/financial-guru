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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Goldman Sachs / Apple Card statement parser.
 *
 * Apple Card PDF format (Goldman Sachs Bank USA):
 *   Jan 15, 2026    Apple                        $1.29
 *   Jan 14, 2026    Amazon.com                  $48.25
 *   Jan 12, 2026    Payment                   ($500.00)
 *
 * Dates: "Jan 15, 2026" or "01/15/2026"
 * Amount: $XX.XX or ($XX.XX) for credits/refunds
 * Parentheses indicate credits on Apple Card statements.
 */
@Component
@Slf4j
public class GoldmanSachsParser extends GenericPdfParser {

    // Primary: "Jan 15, 2026   description   $amount" or "Jan 15, 2026   description   (amount)"
    private static final Pattern GS_TXN_FULL = Pattern.compile(
        "^([A-Z][a-z]{2}\\s+\\d{1,2},\\s+\\d{4})\\s{2,}(.+?)\\s{2,}(\\(\\$?[\\d,]+\\.\\d{2}\\)|[-\\$]?[\\d,]+\\.\\d{2})\\s*$"
    );
    // Fallback: "01/15/2026   description   $amount"
    private static final Pattern GS_TXN_DATE = Pattern.compile(
        "^(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s{2,}(.+?)\\s{2,}(\\(\\$?[\\d,]+\\.\\d{2}\\)|[-\\$]?[\\d,]+\\.\\d{2})\\s*$"
    );
    // Loose fallback
    private static final Pattern GS_TXN_LOOSE = Pattern.compile(
        "^([A-Z][a-z]{2}\\s+\\d{1,2},\\s+\\d{4})\\s+(.+?)\\s+(\\(\\$?[\\d,]+\\.\\d{2}\\)|[-\\$]?[\\d,]+\\.\\d{2})\\s*$"
    );

    private static final List<DateTimeFormatter> GS_DATE_FMTS = List.of(
        DateTimeFormatter.ofPattern("MMM d, yyyy",  Locale.US),
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yy"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yy")
    );

    // ── Statement period ──────────────────────────────────────────────────────
    // "Billing Period  Jan 1, 2026 - Jan 31, 2026"
    private static final Pattern PERIOD_MONTH = Pattern.compile(
        "(?i)(?:billing\\s+period|statement\\s+period)[:\\s]+([A-Z][a-z]{2}\\s+\\d{1,2},\\s+\\d{4})\\s*[-–]\\s*([A-Z][a-z]{2}\\s+\\d{1,2},\\s+\\d{4})"
    );
    private static final Pattern PERIOD_SLASH = Pattern.compile(
        "(?i)(?:billing\\s+period|statement\\s+period)[:\\s]+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s*[-–]\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── Account info ──────────────────────────────────────────────────────────
    // "Card Number  ···· ···· ···· 1234"
    private static final Pattern ACCOUNT_LAST4 = Pattern.compile(
        "(?i)(?:card\\s+number|account\\s+number)[:\\s]+(?:[·•*x\\d]{4}[\\s-]*){3}(\\d{4})"
    );
    // Also simple "ending in 1234"
    private static final Pattern ACCOUNT_ENDING = Pattern.compile(
        "(?i)(?:account|card)\\s+ending(?:\\s+in)?\\s+(\\d{4})"
    );
    // "Total Balance  $1,234.56" or "New Balance  $1,234.56"
    private static final Pattern TOTAL_BALANCE = Pattern.compile(
        "(?im)^(?:total|new)\\s+balance\\s+\\$?([\\d,]+\\.\\d{2})\\s*$"
    );
    // "Credit Limit  $5,000"
    private static final Pattern CREDIT_LIMIT = Pattern.compile(
        "(?i)credit\\s+limit[:\\s]+\\$?([\\d,]+(?:\\.\\d{2})?)"
    );
    // "Available Credit  $3,765.44"
    private static final Pattern AVAILABLE_CREDIT = Pattern.compile(
        "(?i)available\\s+credit[:\\s]+\\$?([\\d,]+(?:\\.\\d{2})?)"
    );

    // ── Payment summary ───────────────────────────────────────────────────────
    private static final Pattern MIN_PAYMENT = Pattern.compile(
        "(?i)minimum\\s+(?:payment\\s+)?due[:\\s]+\\$?([\\d,]+\\.\\d{2})"
    );
    private static final Pattern PAYMENT_DUE_DATE = Pattern.compile(
        "(?i)payment\\s+due\\s+(?:date)?[:\\s]+(\\d{1,2}/\\d{1,2}/\\d{2,4}|[A-Z][a-z]{2}\\s+\\d{1,2},\\s+\\d{4})"
    );

    // ── APR ───────────────────────────────────────────────────────────────────
    // "Variable APR  28.49%"  or  "Purchase APR  28.49%"
    private static final Pattern PURCHASE_APR = Pattern.compile(
        "(?i)(?:variable\\s+|purchase\\s+)?(?:purchase\\s+)?apr[:\\s]+(\\d{1,2}\\.\\d{2})%"
    );
    // Promo: "0.00% intro APR  through  MM/DD/YYYY"
    private static final Pattern PROMO_APR = Pattern.compile(
        "(?i)(\\d{1,2}\\.\\d{2})%\\s+(?:intro(?:ductory)?\\s+)?apr[^\\n]*(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── YTD totals ────────────────────────────────────────────────────────────
    private static final Pattern YTD_FEES = Pattern.compile(
        "(?i)(?:total\\s+)?fees\\s+(?:charged\\s+)?(?:in\\s+(\\d{4}))?[:\\s]+\\$?([\\d,]+\\.\\d{2})"
    );
    private static final Pattern YTD_INTEREST = Pattern.compile(
        "(?i)(?:total\\s+)?interest\\s+(?:charged\\s+)?(?:in\\s+(\\d{4}))?[:\\s]+\\$?([\\d,]+\\.\\d{2})"
    );

    @Override
    public boolean supports(String institution) {
        return "GOLDMAN_SACHS".equals(institution);
    }

    @Override
    public void extractAccountInfo(String pdfText, Account account) {
        if (account == null || pdfText == null) return;

        account.setType(Account.AccountType.CREDIT_CARD);

        if (account.getLast4() == null) {
            Matcher m = ACCOUNT_LAST4.matcher(pdfText);
            if (m.find()) {
                account.setLast4(m.group(1));
                log.info("GoldmanSachs: last4 = {}", m.group(1));
            } else {
                m = ACCOUNT_ENDING.matcher(pdfText);
                if (m.find()) {
                    account.setLast4(m.group(1));
                    log.info("GoldmanSachs: last4 (ending) = {}", m.group(1));
                }
            }
        }

        Matcher balM = TOTAL_BALANCE.matcher(pdfText);
        if (balM.find()) {
            try {
                account.setCurrentBalance(parseAmount(balM.group(1)));
                log.info("GoldmanSachs: current balance = {}", balM.group(1));
            } catch (Exception e) {
                log.debug("GoldmanSachs: could not parse balance");
            }
        }

        if (account.getCreditLimit() == null) {
            Matcher m = CREDIT_LIMIT.matcher(pdfText);
            if (m.find()) {
                try {
                    String val = m.group(1);
                    if (!val.contains(".")) val = val + ".00";
                    account.setCreditLimit(parseAmount(val));
                    log.info("GoldmanSachs: credit limit = {}", val);
                } catch (Exception e) {
                    log.debug("GoldmanSachs: could not parse credit limit");
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
                    log.info("GoldmanSachs: available credit = {}", val);
                } catch (Exception e) {
                    log.debug("GoldmanSachs: could not parse available credit");
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
                        LocalDate expiry = parseGsDate(m.group(2));
                        if (expiry != null) account.setPromoAprEndDate(expiry);
                        log.info("GoldmanSachs: promo APR = {}% expires {}", promo, expiry);
                    }
                } catch (Exception e) {
                    log.debug("GoldmanSachs: could not parse promo APR");
                }
            }
        }

        if (account.getApr() == null) {
            Matcher m = PURCHASE_APR.matcher(pdfText);
            if (m.find()) {
                try {
                    account.setApr(new BigDecimal(m.group(1)));
                    log.info("GoldmanSachs: APR = {}%", m.group(1));
                } catch (Exception e) {
                    log.debug("GoldmanSachs: could not parse APR");
                }
            }
        }
    }

    @Override
    public List<Transaction> parse(String pdfText, Statement statement, Account account) {
        log.info("Using Goldman Sachs (Apple Card) parser");

        extractPaymentSummary(pdfText, statement);
        extractYtdTotals(pdfText, statement);

        LocalDate[] period = detectPeriod(pdfText, statement);
        log.info("GoldmanSachs statement period: {} to {}", period[0], period[1]);

        List<Transaction> transactions = new ArrayList<>();
        String[] lines = pdfText.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) continue;

            String lower = line.toLowerCase();
            // Skip header/footer
            if (lower.startsWith("date") || lower.startsWith("transaction") ||
                lower.startsWith("description") || lower.startsWith("amount") ||
                lower.contains("page ") || lower.contains("continued on")) continue;

            // Try "Jan 15, 2026   desc   $amount" first
            Matcher m = GS_TXN_FULL.matcher(line);
            String dateStr, desc, amtStr;
            if (m.matches()) {
                dateStr = m.group(1);
                desc    = m.group(2).trim();
                amtStr  = m.group(3);
            } else {
                m = GS_TXN_DATE.matcher(line);
                if (m.matches()) {
                    dateStr = m.group(1);
                    desc    = m.group(2).trim();
                    amtStr  = m.group(3);
                } else {
                    m = GS_TXN_LOOSE.matcher(line);
                    if (!m.matches()) continue;
                    dateStr = m.group(1);
                    desc    = m.group(2).trim();
                    amtStr  = m.group(3);
                }
            }

            try {
                if (desc.equalsIgnoreCase("description") || desc.equalsIgnoreCase("amount")) continue;
                if (desc.toLowerCase().startsWith("total ") || desc.toLowerCase().startsWith("new balance")) continue;

                LocalDate date = parseGsDate(dateStr);
                if (date == null) continue;

                // Apple Card uses parentheses for credits: ($500.00)
                BigDecimal amount = parseGsAmount(amtStr);

                Transaction.TransactionType type;
                String descUpper = desc.toUpperCase();
                boolean isCredit = amtStr.startsWith("(") || amount.compareTo(BigDecimal.ZERO) < 0;
                if (isCredit || descUpper.contains("PAYMENT") || descUpper.contains("REFUND") ||
                    descUpper.contains("CREDIT") || descUpper.contains("RETURN")) {
                    type = Transaction.TransactionType.CREDIT;
                } else if (lower.contains("interest")) {
                    type = Transaction.TransactionType.INTEREST;
                } else if (lower.contains(" fee")) {
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

                log.debug("GoldmanSachs parsed: {} | {} | {}", date, merchant, amount);
            } catch (Exception e) {
                log.debug("GoldmanSachs: could not parse line: {} — {}", line, e.getMessage());
            }
        }

        log.info("Goldman Sachs parser extracted {} transactions", transactions.size());

        if (transactions.isEmpty()) {
            log.warn("Goldman Sachs parser found 0 transactions — falling back to generic");
            return super.parse(pdfText, statement, account);
        }
        return transactions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDate parseGsDate(String dateStr) {
        if (dateStr == null) return null;
        for (DateTimeFormatter fmt : GS_DATE_FMTS) {
            try {
                LocalDate d = LocalDate.parse(dateStr.trim(), fmt);
                if (d.getYear() < 100) d = d.withYear(d.getYear() + 2000);
                return d;
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    /**
     * Parse amount handling Apple Card's parenthesis format: ($500.00) means credit/payment.
     */
    private BigDecimal parseGsAmount(String amtStr) {
        if (amtStr == null) return BigDecimal.ZERO;
        String cleaned = amtStr.trim();
        boolean negative = cleaned.startsWith("(") && cleaned.endsWith(")");
        // Remove parentheses and $
        cleaned = cleaned.replaceAll("[()$]", "").replace(",", "").trim();
        BigDecimal result = new BigDecimal(cleaned);
        return negative ? result.negate() : result;
    }

    private LocalDate[] detectPeriod(String text, Statement statement) {
        Matcher m = PERIOD_MONTH.matcher(text);
        if (m.find()) {
            LocalDate start = parseGsDate(m.group(1));
            LocalDate end   = parseGsDate(m.group(2));
            if (start != null && end != null) return new LocalDate[]{start, end};
        }
        m = PERIOD_SLASH.matcher(text);
        if (m.find()) {
            LocalDate start = parseGsDate(m.group(1));
            LocalDate end   = parseGsDate(m.group(2));
            if (start != null && end != null) return new LocalDate[]{start, end};
        }
        if (statement.getStartDate() != null && statement.getEndDate() != null) {
            return new LocalDate[]{statement.getStartDate(), statement.getEndDate()};
        }
        LocalDate now = LocalDate.now();
        return new LocalDate[]{now.minusDays(30), now};
    }

    private void extractPaymentSummary(String pdfText, Statement statement) {
        if (statement == null) return;

        Matcher minM = MIN_PAYMENT.matcher(pdfText);
        if (minM.find()) {
            try {
                statement.setMinimumPayment(parseAmount(minM.group(1)));
                log.info("GoldmanSachs: minimum payment = {}", minM.group(1));
            } catch (Exception e) {
                log.debug("GoldmanSachs: could not parse minimum payment");
            }
        }

        Matcher dueM = PAYMENT_DUE_DATE.matcher(pdfText);
        if (dueM.find()) {
            LocalDate d = parseGsDate(dueM.group(1));
            if (d != null) {
                statement.setPaymentDueDate(d);
                log.info("GoldmanSachs: payment due date = {}", d);
            }
        }
    }

    private void extractYtdTotals(String pdfText, Statement statement) {
        if (statement == null) return;

        Matcher feesM = YTD_FEES.matcher(pdfText);
        if (feesM.find()) {
            try {
                statement.setYtdTotalFees(parseAmount(feesM.group(2)));
                if (feesM.group(1) != null) {
                    statement.setYtdYear(Integer.parseInt(feesM.group(1)));
                }
                log.info("GoldmanSachs: YTD fees = {}", feesM.group(2));
            } catch (Exception e) {
                log.debug("GoldmanSachs: could not parse YTD fees");
            }
        }

        Matcher intM = YTD_INTEREST.matcher(pdfText);
        if (intM.find()) {
            try {
                statement.setYtdTotalInterest(parseAmount(intM.group(2)));
                if (statement.getYtdYear() == null && intM.group(1) != null) {
                    statement.setYtdYear(Integer.parseInt(intM.group(1)));
                }
                log.info("GoldmanSachs: YTD interest = {}", intM.group(2));
            } catch (Exception e) {
                log.debug("GoldmanSachs: could not parse YTD interest");
            }
        }
    }
}

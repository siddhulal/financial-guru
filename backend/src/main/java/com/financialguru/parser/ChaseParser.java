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
 * Chase credit card statement parser.
 *
 * Chase PDF format (PDFBox text extraction):
 *   Date of
 *   Transaction  Merchant Name or Transaction Description  $ Amount
 *   02/12     AUTOMATIC PAYMENT - THANK YOU -40.00
 *   01/15     AMAZON.COM*1A2B3C4D5  SEATTLE WA  23.45
 *
 * Date: MM/DD (no year — inferred from statement period)
 * Amount: no $ prefix, negative = payment/credit
 */
@Component
@Slf4j
public class ChaseParser extends GenericPdfParser {

    // MM/DD followed by description and amount (no $ prefix on amount)
    private static final Pattern CHASE_TXN = Pattern.compile(
        "^(\\d{2}/\\d{2})\\s{2,}(.+?)\\s{2,}(-?[\\d,]+\\.\\d{2})\\s*$"
    );

    // Fallback: single space between description and amount
    private static final Pattern CHASE_TXN_LOOSE = Pattern.compile(
        "^(\\d{2}/\\d{2})\\s+(.+?)\\s+(-?[\\d,]+\\.\\d{2})\\s*$"
    );

    // ── Statement period ──────────────────────────────────────────────────────
    // "Opening/Closing Date 01/16/26 - 02/15/26"
    private static final Pattern OPENING_CLOSING = Pattern.compile(
        "(?i)opening/closing\\s+date\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s*[-–]\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── Account info ──────────────────────────────────────────────────────────
    // "Account Number:  XXXX XXXX XXXX 7844"
    private static final Pattern ACCOUNT_LAST4 = Pattern.compile(
        "(?i)account\\s+number:\\s+(?:X{4}\\s+){3}(\\d{4})"
    );
    // "New Balance $3,152.95"  (exact line — avoid the header "March 2026 New Balance")
    private static final Pattern NEW_BALANCE = Pattern.compile(
        "(?im)^new\\s+balance\\s+\\$?([\\d,]+\\.\\d{2})\\s*$"
    );
    // "Credit Limit $3,600" OR "Credit Access Line $5,600"  (may not have decimal)
    private static final Pattern CREDIT_LIMIT = Pattern.compile(
        "(?i)(?:credit\\s+limit|credit\\s+access\\s+line)\\s+\\$?([\\d,]+(?:\\.\\d{2})?)"
    );
    // "A`vailable Credit $447" OR "A`vailable Credit $4,972"  (backtick OCR artifact)
    private static final Pattern AVAILABLE_CREDIT = Pattern.compile(
        "(?i)a\\W?vailable\\s+(?:credit|for\\s+purchase)\\s+\\$?([\\d,]+(?:\\.\\d{2})?)"
    );

    // ── APR ───────────────────────────────────────────────────────────────────
    // "Purchases 27.74% (d) - ..."  (regular APR row)
    private static final Pattern REGULAR_APR = Pattern.compile(
        "(?i)^purchases\\s+(\\d{1,2}\\.\\d{2})%",
        Pattern.MULTILINE
    );
    // "Purchases 0.00% (d) 09/15/26 ..."  (promo row has expiry date)
    private static final Pattern PROMO_APR_ROW = Pattern.compile(
        "(?i)^purchases\\s+(\\d{1,2}\\.\\d{2})%[^\\n]*(\\d{2}/\\d{2}/\\d{2,4})",
        Pattern.MULTILINE
    );

    // ── Payment summary ───────────────────────────────────────────────────────
    // Chase uses same YTD format as BofA
    private static final Pattern YTD_FEES = Pattern.compile(
        "(?i)total\\s+fees\\s+charged\\s+in\\s+(\\d{4})\\s+\\$?([\\d,]+\\.\\d{2})"
    );
    private static final Pattern YTD_INTEREST = Pattern.compile(
        "(?i)total\\s+interest\\s+charged\\s+in\\s+(\\d{4})\\s+\\$?([\\d,]+\\.\\d{2})"
    );
    // Payment due date — Chase layout makes this hard to reliably extract;
    // the header column bleeds into calendar text. Try best-effort.
    private static final Pattern PAYMENT_DUE_DATE = Pattern.compile(
        "(?i)payment\\s+due\\s+date.*?(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );
    private static final Pattern MIN_PAYMENT_LINE = Pattern.compile(
        "(?i)minimum\\s+payment\\s+due.*?\\$?([\\d,]+\\.\\d{2})"
    );

    @Override
    public boolean supports(String institution) {
        return "CHASE".equals(institution);
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
                log.info("Chase: last4 = {}", m.group(1));
            }
        }

        // Current balance (always update)
        Matcher balM = NEW_BALANCE.matcher(pdfText);
        if (balM.find()) {
            try {
                account.setCurrentBalance(parseAmount(balM.group(1)));
                log.info("Chase: current balance = {}", balM.group(1));
            } catch (Exception e) {
                log.debug("Chase: could not parse new balance");
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
                    log.info("Chase: credit limit = {}", val);
                } catch (Exception e) {
                    log.debug("Chase: could not parse credit limit");
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
                    log.info("Chase: available credit = {}", val);
                } catch (Exception e) {
                    log.debug("Chase: could not parse available credit");
                }
            }
        }

        // APR — look for promo row first (has expiry date), then regular row
        if (account.getPromoApr() == null) {
            Matcher m = PROMO_APR_ROW.matcher(pdfText);
            if (m.find()) {
                try {
                    BigDecimal promo = new BigDecimal(m.group(1));
                    // Only treat as promo if it's lower than a plausible regular APR
                    if (promo.compareTo(BigDecimal.valueOf(10)) < 0) {
                        account.setPromoApr(promo);
                        LocalDate expiry = parseShortDate(m.group(2));
                        if (expiry != null) account.setPromoAprEndDate(expiry);
                        log.info("Chase: promo APR = {}% expires {}", promo, expiry);
                    }
                } catch (Exception e) {
                    log.debug("Chase: could not parse promo APR");
                }
            }
        }

        if (account.getApr() == null) {
            // Find the regular (non-promo) purchase APR — usually the last "Purchases XX.XX%" line
            Matcher m = REGULAR_APR.matcher(pdfText);
            BigDecimal lastApr = null;
            while (m.find()) {
                try {
                    BigDecimal candidate = new BigDecimal(m.group(1));
                    if (candidate.compareTo(BigDecimal.valueOf(5)) > 0) {
                        lastApr = candidate; // take the regular (non-zero) APR
                    }
                } catch (Exception ignored) {}
            }
            if (lastApr != null) {
                account.setApr(lastApr);
                log.info("Chase: APR = {}%", lastApr);
            }
        }
    }

    @Override
    public List<Transaction> parse(String pdfText, Statement statement, Account account) {
        log.info("Using Chase-specific parser");

        // Extract payment/YTD info regardless of transaction parse result
        extractPaymentSummary(pdfText, statement);
        extractYtdTotals(pdfText, statement);

        // Detect statement period for year inference
        LocalDate[] period = detectPeriod(pdfText, statement);
        LocalDate periodStart = period[0];
        LocalDate periodEnd   = period[1];
        log.info("Chase statement period: {} to {}", periodStart, periodEnd);

        // Infer payment due date from closing date if not found in text.
        // Chase PDF header values ($35.00, 01/14/26) are in a non-text layer that
        // PDFBox cannot extract. Chase typically sets due date = closing + 28 days.
        if (statement.getPaymentDueDate() == null && periodEnd != null) {
            statement.setPaymentDueDate(periodEnd.plusDays(28));
            log.info("Chase: inferred payment due date = {} (closing {} + 28 days)", periodEnd.plusDays(28), periodEnd);
        }

        List<Transaction> transactions = new ArrayList<>();
        boolean inTransactionSection = false;
        String[] lines = pdfText.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) continue;

            // Section markers
            String lower = line.toLowerCase();
            if (lower.contains("date of") || lower.contains("transaction merchant") ||
                lower.contains("account activity") || lower.contains("transaction detail")) {
                inTransactionSection = true;
                continue;
            }
            if (lower.startsWith("2026") || lower.startsWith("totals year") ||
                lower.startsWith("total fees") || lower.startsWith("total interest") ||
                lower.startsWith("your annual percentage")) {
                inTransactionSection = false;
            }

            if (!inTransactionSection) continue;

            // Try strict pattern (2+ spaces between fields) first
            Matcher m = CHASE_TXN.matcher(line);
            if (!m.matches()) {
                m = CHASE_TXN_LOOSE.matcher(line);
                if (!m.matches()) continue;
            }

            try {
                String dateStr  = m.group(1);
                String desc     = m.group(2).trim();
                String amtStr   = m.group(3);

                if (desc.equalsIgnoreCase("description") || desc.equalsIgnoreCase("amount")) continue;

                LocalDate date = resolveDate(dateStr, periodStart, periodEnd);
                if (date == null) continue;

                BigDecimal amount = parseAmount(amtStr);

                Transaction.TransactionType type;
                if (desc.toUpperCase().contains("PAYMENT") || desc.toUpperCase().contains("AUTOPAY") ||
                    amount.compareTo(BigDecimal.ZERO) < 0) {
                    type = Transaction.TransactionType.CREDIT;
                } else if (lower.contains("interest")) {
                    type = Transaction.TransactionType.INTEREST;
                } else if (lower.contains("fee")) {
                    type = Transaction.TransactionType.FEE;
                } else {
                    type = Transaction.TransactionType.DEBIT;
                }

                String merchant  = normalizeMerchant(desc);
                String category  = categorize(merchant, type);

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

                log.debug("Chase parsed: {} | {} | {}", date, merchant, amount);
            } catch (Exception e) {
                log.debug("Chase: could not parse line: {} — {}", line, e.getMessage());
            }
        }

        log.info("Chase parser extracted {} transactions", transactions.size());

        if (transactions.isEmpty()) {
            log.warn("Chase parser found 0 transactions — falling back to generic");
            return super.parse(pdfText, statement, account);
        }
        return transactions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void extractPaymentSummary(String pdfText, Statement statement) {
        if (statement == null) return;

        Matcher minM = MIN_PAYMENT_LINE.matcher(pdfText);
        if (minM.find()) {
            try {
                statement.setMinimumPayment(parseAmount(minM.group(1)));
                log.info("Chase: minimum payment = {}", minM.group(1));
            } catch (Exception e) {
                log.debug("Chase: could not parse minimum payment");
            }
        }

        Matcher dueM = PAYMENT_DUE_DATE.matcher(pdfText);
        if (dueM.find()) {
            LocalDate d = parseShortDate(dueM.group(1));
            if (d != null) {
                statement.setPaymentDueDate(d);
                log.info("Chase: payment due date = {}", d);
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
                log.info("Chase: YTD fees ({}) = {}", feesM.group(1), feesM.group(2));
            } catch (Exception e) {
                log.debug("Chase: could not parse YTD fees");
            }
        }

        Matcher intM = YTD_INTEREST.matcher(pdfText);
        if (intM.find()) {
            try {
                statement.setYtdTotalInterest(parseAmount(intM.group(2)));
                if (statement.getYtdYear() == null) {
                    statement.setYtdYear(Integer.parseInt(intM.group(1)));
                }
                log.info("Chase: YTD interest ({}) = {}", intM.group(1), intM.group(2));
            } catch (Exception e) {
                log.debug("Chase: could not parse YTD interest");
            }
        }
    }

    private LocalDate[] detectPeriod(String text, Statement statement) {
        Matcher m = OPENING_CLOSING.matcher(text);
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

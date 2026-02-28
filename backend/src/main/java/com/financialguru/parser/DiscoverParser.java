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
 * Discover credit card statement parser.
 *
 * Discover PDF format:
 *   Trans.Date  Post Date  Description                    Amount
 *   01/15/26    01/16/26   AMAZON.COM                     $48.25
 *   01/12/26    01/14/26   PAYMENT RECEIVED - THANK YOU  -$500.00
 *
 * Dates: MM/DD/YY or MM/DD/YYYY
 * Amount: $XX.XX prefix, negative = payment/credit
 */
@Component
@Slf4j
public class DiscoverParser extends GenericPdfParser {

    // Two-date format: MM/DD/YY  MM/DD/YY  description  $amount
    private static final Pattern DISC_TXN_2DATE = Pattern.compile(
        "^(\\d{2}/\\d{2}/\\d{2,4})\\s+(\\d{2}/\\d{2}/\\d{2,4})\\s+(.+?)\\s{2,}(-?\\$?[\\d,]+\\.\\d{2})\\s*$"
    );
    // Single-date: MM/DD/YY  description  $amount
    private static final Pattern DISC_TXN_1DATE = Pattern.compile(
        "^(\\d{2}/\\d{2}/\\d{2,4})\\s{2,}(.+?)\\s{2,}(-?\\$?[\\d,]+\\.\\d{2})\\s*$"
    );
    // Loose single-date fallback
    private static final Pattern DISC_TXN_LOOSE = Pattern.compile(
        "^(\\d{2}/\\d{2}/\\d{2,4})\\s+(.+?)\\s+(-?\\$?[\\d,]+\\.\\d{2})\\s*$"
    );

    // ── Statement period ──────────────────────────────────────────────────────
    // "Statement Period  MM/DD/YY  -  MM/DD/YY"
    private static final Pattern PERIOD = Pattern.compile(
        "(?i)(?:statement\\s+period|billing\\s+period|closing\\s+date)[:\\s]+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s*[-–]\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );
    // "Opening Date MM/DD/YYYY   Closing Date MM/DD/YYYY"
    private static final Pattern OPENING_CLOSING = Pattern.compile(
        "(?i)(?:opening|open)\\s+date[:\\s]+(\\d{1,2}/\\d{1,2}/\\d{2,4})[^\\n]*(?:closing|close)\\s+date[:\\s]+(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── Account info ──────────────────────────────────────────────────────────
    // "Account Ending XXXX" or "Card Ending XXXX"
    private static final Pattern ACCOUNT_LAST4 = Pattern.compile(
        "(?i)(?:account|card)\\s+ending\\s+(?:in\\s+)?(\\d{4})"
    );
    // "New Balance  $1,234.56"
    private static final Pattern NEW_BALANCE = Pattern.compile(
        "(?im)^new\\s+balance\\s+\\$?([\\d,]+\\.\\d{2})\\s*$"
    );
    // "Credit Limit  $5,000" or "Total Credit Line  $5,000"
    private static final Pattern CREDIT_LIMIT = Pattern.compile(
        "(?i)(?:credit\\s+limit|total\\s+credit\\s+line|credit\\s+line)[:\\s]+\\$?([\\d,]+(?:\\.\\d{2})?)"
    );
    // "Available Credit  $3,765.44"
    private static final Pattern AVAILABLE_CREDIT = Pattern.compile(
        "(?i)available\\s+credit[:\\s]+\\$?([\\d,]+(?:\\.\\d{2})?)"
    );
    // "Cashback Bonus Balance  $XX.XX"
    private static final Pattern CASHBACK_BALANCE = Pattern.compile(
        "(?i)cashback\\s+bonus\\s+balance[:\\s]+\\$?([\\d,]+\\.\\d{2})"
    );

    // ── Payment summary ───────────────────────────────────────────────────────
    // "Minimum Payment Due:  $25.00"
    private static final Pattern MIN_PAYMENT = Pattern.compile(
        "(?i)minimum\\s+payment\\s+due[:\\s]+\\$?([\\d,]+\\.\\d{2})"
    );
    // "Payment Due Date:  01/15/26"
    private static final Pattern PAYMENT_DUE_DATE = Pattern.compile(
        "(?i)payment\\s+due\\s+date[:\\s]+(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── APR ───────────────────────────────────────────────────────────────────
    // "Purchase APR  27.49%  Variable"
    private static final Pattern PURCHASE_APR = Pattern.compile(
        "(?i)purchase\\s+apr[:\\s]+(\\d{1,2}\\.\\d{2})%"
    );
    // Promo: "0.00%  Intro APR  through  MM/DD/YYYY"
    private static final Pattern PROMO_APR = Pattern.compile(
        "(?i)(\\d{1,2}\\.\\d{2})%\\s+(?:intro(?:ductory)?\\s+)?apr[^\\n]*(\\d{1,2}/\\d{1,2}/\\d{2,4})"
    );

    // ── YTD totals ────────────────────────────────────────────────────────────
    // "Fees Charged in 2026  $0.00"
    private static final Pattern YTD_FEES = Pattern.compile(
        "(?i)(?:total\\s+)?fees\\s+charged\\s+(?:in\\s+(\\d{4}))?[:\\s]+\\$?([\\d,]+\\.\\d{2})"
    );
    // "Interest Charged in 2026  $0.00"
    private static final Pattern YTD_INTEREST = Pattern.compile(
        "(?i)(?:total\\s+)?interest\\s+charged\\s+(?:in\\s+(\\d{4}))?[:\\s]+\\$?([\\d,]+\\.\\d{2})"
    );

    @Override
    public boolean supports(String institution) {
        return "DISCOVER".equals(institution);
    }

    @Override
    public void extractAccountInfo(String pdfText, Account account) {
        if (account == null || pdfText == null) return;

        account.setType(Account.AccountType.CREDIT_CARD);

        if (account.getLast4() == null) {
            Matcher m = ACCOUNT_LAST4.matcher(pdfText);
            if (m.find()) {
                account.setLast4(m.group(1));
                log.info("Discover: last4 = {}", m.group(1));
            }
        }

        Matcher balM = NEW_BALANCE.matcher(pdfText);
        if (balM.find()) {
            try {
                account.setCurrentBalance(parseAmount(balM.group(1)));
                log.info("Discover: current balance = {}", balM.group(1));
            } catch (Exception e) {
                log.debug("Discover: could not parse new balance");
            }
        }

        if (account.getCreditLimit() == null) {
            Matcher m = CREDIT_LIMIT.matcher(pdfText);
            if (m.find()) {
                try {
                    String val = m.group(1);
                    if (!val.contains(".")) val = val + ".00";
                    account.setCreditLimit(parseAmount(val));
                    log.info("Discover: credit limit = {}", val);
                } catch (Exception e) {
                    log.debug("Discover: could not parse credit limit");
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
                    log.info("Discover: available credit = {}", val);
                } catch (Exception e) {
                    log.debug("Discover: could not parse available credit");
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
                        LocalDate expiry = parseShortDate(m.group(2));
                        if (expiry != null) account.setPromoAprEndDate(expiry);
                        log.info("Discover: promo APR = {}% expires {}", promo, expiry);
                    }
                } catch (Exception e) {
                    log.debug("Discover: could not parse promo APR");
                }
            }
        }

        if (account.getApr() == null) {
            Matcher m = PURCHASE_APR.matcher(pdfText);
            if (m.find()) {
                try {
                    account.setApr(new BigDecimal(m.group(1)));
                    log.info("Discover: APR = {}%", m.group(1));
                } catch (Exception e) {
                    log.debug("Discover: could not parse APR");
                }
            }
        }
    }

    @Override
    public List<Transaction> parse(String pdfText, Statement statement, Account account) {
        log.info("Using Discover parser");

        extractPaymentSummary(pdfText, statement);
        extractYtdTotals(pdfText, statement);

        LocalDate[] period = detectPeriod(pdfText, statement);
        log.info("Discover statement period: {} to {}", period[0], period[1]);

        List<Transaction> transactions = new ArrayList<>();
        boolean inTransactionSection = false;
        String[] lines = pdfText.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) continue;

            String lower = line.toLowerCase();

            // Section markers
            if (lower.contains("transaction description") || lower.contains("account activity") ||
                lower.contains("purchases and cash advances") || lower.contains("new transactions") ||
                lower.contains("payments and credits")) {
                inTransactionSection = true;
                continue;
            }
            if (lower.startsWith("fees") || lower.startsWith("interest charged") ||
                lower.startsWith("total purchases") || lower.startsWith("total payments") ||
                lower.startsWith("cashback bonus") || lower.startsWith("interest charge")) {
                inTransactionSection = false;
            }

            // Try two-date pattern first (Discover uses trans + post date)
            Matcher m = DISC_TXN_2DATE.matcher(line);
            String dateStr, desc, amtStr;
            if (m.matches()) {
                dateStr = m.group(1);  // transaction date
                desc    = m.group(3).trim();
                amtStr  = m.group(4);
            } else {
                m = DISC_TXN_1DATE.matcher(line);
                if (m.matches()) {
                    dateStr = m.group(1);
                    desc    = m.group(2).trim();
                    amtStr  = m.group(3);
                } else {
                    m = DISC_TXN_LOOSE.matcher(line);
                    if (!m.matches()) continue;
                    dateStr = m.group(1);
                    desc    = m.group(2).trim();
                    amtStr  = m.group(3);
                }
            }

            try {
                if (desc.equalsIgnoreCase("description") || desc.equalsIgnoreCase("amount")) continue;
                if (desc.toLowerCase().startsWith("total ") || desc.toLowerCase().startsWith("new balance")) continue;

                LocalDate date = parseShortDate(dateStr);
                if (date == null) continue;

                BigDecimal amount = parseAmount(amtStr.replace("$", ""));

                Transaction.TransactionType type;
                String descUpper = desc.toUpperCase();
                if (descUpper.contains("PAYMENT") || descUpper.contains("PAYMENT RECEIVED") ||
                    descUpper.contains("CREDIT ADJUSTMENT") || descUpper.contains("CASHBACK BONUS") ||
                    amount.compareTo(BigDecimal.ZERO) < 0) {
                    type = Transaction.TransactionType.CREDIT;
                } else if (lower.contains("interest charge")) {
                    type = Transaction.TransactionType.INTEREST;
                } else if (lower.contains(" fee") || lower.contains("annual fee") || lower.contains("late fee")) {
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

                log.debug("Discover parsed: {} | {} | {}", date, merchant, amount);
            } catch (Exception e) {
                log.debug("Discover: could not parse line: {} — {}", line, e.getMessage());
            }
        }

        log.info("Discover parser extracted {} transactions", transactions.size());

        if (transactions.isEmpty()) {
            log.warn("Discover parser found 0 transactions — falling back to generic");
            return super.parse(pdfText, statement, account);
        }
        return transactions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private LocalDate[] detectPeriod(String text, Statement statement) {
        Matcher m = OPENING_CLOSING.matcher(text);
        if (m.find()) {
            LocalDate start = parseShortDate(m.group(1));
            LocalDate end   = parseShortDate(m.group(2));
            if (start != null && end != null) return new LocalDate[]{start, end};
        }
        m = PERIOD.matcher(text);
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

    private void extractPaymentSummary(String pdfText, Statement statement) {
        if (statement == null) return;

        Matcher minM = MIN_PAYMENT.matcher(pdfText);
        if (minM.find()) {
            try {
                statement.setMinimumPayment(parseAmount(minM.group(1)));
                log.info("Discover: minimum payment = {}", minM.group(1));
            } catch (Exception e) {
                log.debug("Discover: could not parse minimum payment");
            }
        }

        Matcher dueM = PAYMENT_DUE_DATE.matcher(pdfText);
        if (dueM.find()) {
            LocalDate d = parseShortDate(dueM.group(1));
            if (d != null) {
                statement.setPaymentDueDate(d);
                log.info("Discover: payment due date = {}", d);
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
                log.info("Discover: YTD fees = {}", feesM.group(2));
            } catch (Exception e) {
                log.debug("Discover: could not parse YTD fees");
            }
        }

        Matcher intM = YTD_INTEREST.matcher(pdfText);
        if (intM.find()) {
            try {
                statement.setYtdTotalInterest(parseAmount(intM.group(2)));
                if (statement.getYtdYear() == null && intM.group(1) != null) {
                    statement.setYtdYear(Integer.parseInt(intM.group(1)));
                }
                log.info("Discover: YTD interest = {}", intM.group(2));
            } catch (Exception e) {
                log.debug("Discover: could not parse YTD interest");
            }
        }
    }
}

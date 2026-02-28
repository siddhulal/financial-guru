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
 * Generic fallback PDF parser using regex patterns to extract transactions.
 * Handles common bank statement formats.
 */
@Component
@Slf4j
public class GenericPdfParser implements BankStatementParser {

    // Common date formats in bank statements
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yy"),
        DateTimeFormatter.ofPattern("MMM dd, yyyy"),
        DateTimeFormatter.ofPattern("MMM d, yyyy"),
        DateTimeFormatter.ofPattern("dd MMM yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    // Pattern: date, description, amount (handles negative/positive)
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile(
        "(\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?|\\w{3}\\s+\\d{1,2},?\\s*\\d{4}?)\\s+" +
        "(.{10,60}?)\\s+" +
        "(-?\\$?\\d{1,3}(?:,\\d{3})*\\.\\d{2})",
        Pattern.MULTILINE
    );

    @Override
    public boolean supports(String institution) {
        return "GENERIC".equals(institution);
    }

    @Override
    public List<Transaction> parse(String pdfText, Statement statement, Account account) {
        List<Transaction> transactions = new ArrayList<>();
        String[] lines = pdfText.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) continue;

            Matcher m = TRANSACTION_PATTERN.matcher(line);
            if (m.find()) {
                try {
                    String dateStr = m.group(1).trim();
                    String description = m.group(2).trim();
                    String amountStr = m.group(3).trim();

                    LocalDate date = parseDate(dateStr);
                    if (date == null) continue;

                    BigDecimal amount = parseAmount(amountStr);
                    String merchant = normalizeMerchant(description);
                    Transaction.TransactionType txType = amount.compareTo(BigDecimal.ZERO) < 0
                        ? Transaction.TransactionType.CREDIT
                        : Transaction.TransactionType.DEBIT;

                    Transaction t = Transaction.builder()
                        .account(account)
                        .statement(statement)
                        .transactionDate(date)
                        .description(description)
                        .merchantName(merchant)
                        .amount(amount.abs())
                        .type(txType)
                        .category(categorize(merchant, txType))
                        .build();

                    transactions.add(t);
                } catch (Exception e) {
                    log.debug("Could not parse line: {}", line);
                }
            }
        }

        log.info("Generic parser extracted {} transactions", transactions.size());
        return transactions;
    }

    protected LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        // Try adding current year for MM/dd format
        try {
            return LocalDate.parse(dateStr + "/" + LocalDate.now().getYear(),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        } catch (DateTimeParseException ignored) {}
        return null;
    }

    protected BigDecimal parseAmount(String amountStr) {
        String cleaned = amountStr.replace("$", "").trim();
        // Detect comma-as-decimal: e.g., "48,25" (comma followed by exactly 2 digits at end)
        if (cleaned.matches("-?\\d{1,3},\\d{2}")) {
            cleaned = cleaned.replace(",", ".");
        } else {
            // Otherwise commas are thousands separators — strip them
            cleaned = cleaned.replace(",", "");
        }
        return new BigDecimal(cleaned);
    }

    protected String normalizeMerchant(String description) {
        if (description == null) return null;
        // Remove reference numbers, excess spaces, common bank prefixes
        return description
            .replaceAll("\\s+\\d{5,}.*$", "") // Remove trailing reference numbers
            .replaceAll("(?i)^(POS |DDA |ACH |PPD |CCD )", "") // Remove bank codes
            .replaceAll("\\s+", " ")
            .trim();
    }

    /**
     * Rule-based transaction categorization based on merchant name keywords.
     * Returns a category string or null if no match found.
     */
    protected String categorize(String merchantName, Transaction.TransactionType type) {
        if (merchantName == null) return null;
        if (type == Transaction.TransactionType.FEE) return "Fees";
        if (type == Transaction.TransactionType.INTEREST) return "Fees";
        if (type == Transaction.TransactionType.CREDIT) return null;

        String lower = merchantName.toLowerCase();

        if (matchesAny(lower, "wholefds", "whole foods", "kroger", "trader joe", "safeway",
                "publix", "aldi", "patel brothers", "patel brother", "harris teeter",
                "fresh market", "food lion", "wegman", "sprouts", "h-e-b", "market basket",
                "giant", "stop shop", "meijer", "albertsons", "vons", "ralph", "piggly",
                "grocery", "supermarket", "food mart", "fresh fare", "compare foods"))
            return "Groceries";

        if (matchesAny(lower, "restaurant", "kitchen", "grill", "pizza", "sushi", "ramen",
                "taco", "burger", "mcdonald", "chipotle", "panera", "subway",
                "chick-fil", "domino", "doordash", "grubhub", "ubereats", "door dash",
                "uber eats", "postmates", "seamless", "starbucks", "dunkin", "coffee",
                "cafe", "diner", "bistro", "eatery", "barbeque", "bbq", "thai", "chinese",
                "indian restaurant", "desi district", "pho", "wingstop", "five guys",
                "shake shack", "in-n-out", "popeyes", "kfc", "sonic drive", "dairy queen",
                "applebee", "chilis", "olive garden", "red lobster", "ihop", "denny",
                "tst*", "toast", "benihana", "buffalo wild", "outback", "cracker barrel",
                "cheesecake factory", "texas roadhouse", "hooters", "legal sea"))
            return "Dining";

        if (matchesAny(lower, "netflix", "spotify", "hulu", "disney+", "apple.com/bill",
                "google play", "google one", "google *google", "youtube premium", "youtube music",
                "paramount", "peacock", "hbo", "max.com", "showtime", "audible", "amazon prime",
                "apple music", "pandora", "tidal", "crunchyroll", "fubo",
                "microsoft 365", "dropbox", "icloud", "adobe", "1password", "lastpass"))
            return "Subscriptions";

        if (matchesAny(lower, "amazon", "walmart", "target", "costco", "best buy", "ebay",
                "etsy", "apple store", "apple retail", "ikea", "home depot", "lowe",
                "tj maxx", "marshalls", "ross", "nordstrom", "macy", "gap", "old navy",
                "h&m", "zara", "forever 21", "bath body", "victoria secret", "sephora",
                "ulta", "chewy", "petco", "pet smart", "staples", "office depot",
                "dollar tree", "dollar general", "five below",
                "nautica", "gap factory", "banana republic", "j.crew", "ann taylor",
                "dsw", "rack room", "shoe carnival", "famous footwear", "foot locker",
                "burlington coat", "tuesday morning"))
            return "Shopping";

        if (matchesAny(lower, "airline", "airways", "united air", "delta air", "american air",
                "southwest", "jetblue", "alaska air", "spirit air", "frontier air",
                "hotel", "hilton", "marriott", "hyatt", "westin", "sheraton", "ihg",
                "hampton inn", "holiday inn", "airbnb", "vrbo", "expedia", "priceline",
                "booking.com", "hotels.com", "kayak", "travelocity", "hertz", "enterprise rent",
                "avis", "national car", "budget car", "amtrak", "greyhound"))
            return "Travel";

        if (matchesAny(lower, "uber", "lyft", "taxi", "transit", "metro", "mta", "bart",
                "parking", "parkmobile", "spothero", "divvy", "citi bike", "lime",
                "bird scooter"))
            return "Transportation";

        if (matchesAny(lower, "bp oil", "bp #", "shell oil", "exxon", "mobil", "chevron",
                "sunoco", "marathon", "citgo", "getty", "speedway", "wawa", "sheetz",
                "kwik trip", "casey", "circle k", "racetrac", "gas station", "fuel",
                "quiktrip", "7-eleven", "pilot flying"))
            return "Gas";

        if (matchesAny(lower, "pharmacy", "cvs", "walgreen", "rite aid", "hospital", "medical",
                "doctor", "dental", "dentist", "vision", "optometric", "health",
                "urgent care", "clinic", "laboratory", "quest diagnostics", "labcorp",
                "kaiser", "blue cross", "aetna", "cigna", "humana", "insurance"))
            return "Healthcare";

        if (matchesAny(lower, "electric", "gas utility", "water utility", "sewage", "waste",
                "comcast", "xfinity", "spectrum", "cox comm", "at&t", "att.com",
                "verizon", "t-mobile", "sprint", "dish network", "directv",
                "internet service", "phone bill"))
            return "Utilities";

        if (matchesAny(lower, "amc theatre", "regal cinema", "cinemark", "movie", "concert",
                "ticketmaster", "eventbrite", "live nation", "stub hub", "sports ticket",
                "golf", "bowling", "escape room", "dave buster", "arcade", "museum",
                "zoo", "aquarium", "sea life", "theme park", "six flags", "disney world",
                "legoland", "universal studios", "seaworld"))
            return "Entertainment";

        if (matchesAny(lower, "planet fitness", "la fitness", "equinox", "gold gym", "ymca",
                "anytime fitness", "crossfit", "peloton", "beachbody", "gym", "fitness",
                "yoga", "pilates", "sport", "athletic"))
            return "Health & Fitness";

        if (matchesAny(lower, "tuition", "university", "college", "school", "coursera",
                "udemy", "linkedin learning", "skillshare", "pluralsight", "books",
                "textbook", "education", "tutoring", "chegg"))
            return "Education";

        return null;
    }

    private boolean matchesAny(String lower, String... keywords) {
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }
}

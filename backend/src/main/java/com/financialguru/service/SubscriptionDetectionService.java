package com.financialguru.service;

import com.financialguru.model.Account;
import com.financialguru.model.Subscription;
import com.financialguru.model.Transaction;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.SubscriptionRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionDetectionService {

    private final TransactionRepository transactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AccountRepository accountRepository;

    /**
     * Known subscription services: keyword → display name.
     * A single transaction matching any keyword is enough to flag as a subscription.
     */
    private static final List<String[]> KNOWN_SUBS = List.of(
        // [keyword, displayName, category]
        new String[]{"netflix",                 "Netflix",             "Entertainment"},
        new String[]{"spotify",                 "Spotify",             "Entertainment"},
        new String[]{"hulu",                    "Hulu",                "Entertainment"},
        new String[]{"disney+",                 "Disney+",             "Entertainment"},
        new String[]{"disneyplus",              "Disney+",             "Entertainment"},
        new String[]{"hbomax",                  "HBO Max",             "Entertainment"},
        new String[]{"max.com",                 "HBO Max",             "Entertainment"},
        new String[]{"paramount",               "Paramount+",          "Entertainment"},
        new String[]{"peacock",                 "Peacock",             "Entertainment"},
        new String[]{"crunchyroll",             "Crunchyroll",         "Entertainment"},
        new String[]{"fubo",                    "FuboTV",              "Entertainment"},
        new String[]{"apple.com/bill",          "Apple Services",      "Subscriptions"},
        new String[]{"apple music",             "Apple Music",         "Entertainment"},
        new String[]{"youtube premium",         "YouTube Premium",     "Entertainment"},
        new String[]{"youtube music",           "YouTube Music",       "Entertainment"},
        new String[]{"amazon prime",            "Amazon Prime",        "Shopping"},
        new String[]{"prime video",             "Prime Video",         "Entertainment"},
        new String[]{"amazon music",            "Amazon Music",        "Entertainment"},
        new String[]{"audible",                 "Audible",             "Entertainment"},
        new String[]{"kindle unlimited",        "Kindle Unlimited",    "Entertainment"},
        new String[]{"openai",                  "ChatGPT Plus",        "Technology"},
        new String[]{"chatgpt",                 "ChatGPT Plus",        "Technology"},
        new String[]{"google one",              "Google One",          "Technology"},
        new String[]{"google *google",          "Google Services",     "Technology"},
        new String[]{"microsoft 365",           "Microsoft 365",       "Technology"},
        new String[]{"microsoft*",              "Microsoft",           "Technology"},
        new String[]{"adobe",                   "Adobe Creative Cloud","Technology"},
        new String[]{"dropbox",                 "Dropbox",             "Technology"},
        new String[]{"icloud",                  "iCloud",              "Technology"},
        new String[]{"github",                  "GitHub",              "Technology"},
        new String[]{"zoom",                    "Zoom",                "Technology"},
        new String[]{"slack",                   "Slack",               "Technology"},
        new String[]{"1password",               "1Password",           "Technology"},
        new String[]{"lastpass",                "LastPass",            "Technology"},
        new String[]{"nordvpn",                 "NordVPN",             "Technology"},
        new String[]{"expressvpn",              "ExpressVPN",          "Technology"},
        new String[]{"nytimes",                 "NY Times",            "News"},
        new String[]{"wsj.com",                 "Wall Street Journal", "News"},
        new String[]{"wapo",                    "Washington Post",     "News"},
        new String[]{"duolingo",                "Duolingo",            "Education"},
        new String[]{"coursera",                "Coursera",            "Education"},
        new String[]{"udemy",                   "Udemy",               "Education"},
        new String[]{"linkedin learning",       "LinkedIn Learning",   "Education"},
        new String[]{"skillshare",              "Skillshare",          "Education"},
        new String[]{"masterclass",             "MasterClass",         "Education"},
        new String[]{"planet fitness",          "Planet Fitness",      "Health & Fitness"},
        new String[]{"equinox",                 "Equinox",             "Health & Fitness"},
        new String[]{"peloton",                 "Peloton",             "Health & Fitness"},
        new String[]{"headspace",               "Headspace",           "Health & Fitness"},
        new String[]{"calm",                    "Calm",                "Health & Fitness"},
        new String[]{"noom",                    "Noom",                "Health & Fitness"},
        new String[]{"myfitnesspal",            "MyFitnessPal",        "Health & Fitness"},
        new String[]{"xfinity",                 "Xfinity",             "Utilities"},
        new String[]{"comcast",                 "Comcast",             "Utilities"},
        new String[]{"spectrum",                "Spectrum",            "Utilities"},
        new String[]{"verizon",                 "Verizon",             "Utilities"},
        new String[]{"t-mobile",                "T-Mobile",            "Utilities"},
        new String[]{"at&t",                    "AT&T",                "Utilities"},
        new String[]{"att.com",                 "AT&T",                "Utilities"},
        new String[]{"directv",                 "DirecTV",             "Utilities"},
        new String[]{"dish network",            "Dish Network",        "Utilities"}
    );

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Called after each statement is processed. Detects subscriptions in those transactions.
     */
    @Transactional
    public List<Subscription> detectSubscriptions(List<Transaction> transactions, Account account) {
        List<Subscription> found = new ArrayList<>();
        found.addAll(detectKnownSubscriptions(transactions, account));
        found.addAll(detectRecurringPatterns(transactions, account));
        markDuplicates();
        return found;
    }

    /**
     * Scan ALL transactions across all accounts. Useful for rerunning after code changes.
     */
    @Transactional
    public int detectAllSubscriptions() {
        // Clear existing subscriptions so we start fresh
        subscriptionRepository.deleteAll();
        int total = 0;

        List<Account> accounts = accountRepository.findAll();
        for (Account account : accounts) {
            List<Transaction> txns = transactionRepository.findByAccountId(account.getId());
            if (txns.isEmpty()) continue;
            List<Subscription> found = new ArrayList<>();
            found.addAll(detectKnownSubscriptions(txns, account));
            found.addAll(detectRecurringPatterns(txns, account));
            total += found.size();
            log.info("Detected {} subscriptions for account {}", found.size(), account.getName());
        }
        markDuplicates();
        log.info("Total subscriptions detected across all accounts: {}", total);
        return total;
    }

    // ── Pass 1: Known subscription keyword matching ──────────────────────────

    private List<Subscription> detectKnownSubscriptions(List<Transaction> transactions, Account account) {
        List<Subscription> found = new ArrayList<>();

        for (Transaction t : transactions) {
            if (t.getType() == Transaction.TransactionType.CREDIT ||
                t.getType() == Transaction.TransactionType.PAYMENT) continue;

            String haystack = ((t.getMerchantName() != null ? t.getMerchantName() : "") + " " +
                               (t.getDescription() != null  ? t.getDescription()  : "")).toLowerCase();

            for (String[] sub : KNOWN_SUBS) {
                String keyword     = sub[0];
                String displayName = sub[1];
                String category    = sub[2];

                if (!haystack.contains(keyword)) continue;

                // Normalize key = displayName lowercase
                String normKey = displayName.toLowerCase();

                // Skip if already saved for this account
                if (subscriptionRepository.findByNormalizedNameAndAccountId(normKey, account.getId()).isPresent())
                    break;

                // Frequency guess: most digital subs are monthly
                Subscription.SubscriptionFrequency freq = Subscription.SubscriptionFrequency.MONTHLY;
                BigDecimal annualCost = t.getAmount().multiply(BigDecimal.valueOf(12));

                Subscription s = Subscription.builder()
                    .merchantName(t.getMerchantName())
                    .normalizedName(normKey)
                    .amount(t.getAmount())
                    .frequency(freq)
                    .account(account)
                    .firstSeenDate(t.getTransactionDate())
                    .lastChargedDate(t.getTransactionDate())
                    .nextExpectedDate(t.getTransactionDate().plusMonths(1))
                    .timesCharged(1)
                    .annualCost(annualCost)
                    .category(category)
                    .isActive(true)
                    .build();

                found.add(subscriptionRepository.save(s));
                log.info("Known subscription detected: {} (${}) on account {}",
                    displayName, t.getAmount(), account.getName());
                break; // Don't match multiple keywords for same transaction
            }
        }
        return found;
    }

    // ── Pass 2: Recurring pattern detection ──────────────────────────────────

    private List<Subscription> detectRecurringPatterns(List<Transaction> transactions, Account account) {
        List<Subscription> found = new ArrayList<>();

        Map<String, List<Transaction>> byMerchant = transactions.stream()
            .filter(t -> t.getMerchantName() != null)
            .filter(t -> t.getType() != Transaction.TransactionType.CREDIT &&
                         t.getType() != Transaction.TransactionType.PAYMENT)
            .collect(Collectors.groupingBy(t -> roughNormalize(t.getMerchantName())));

        for (Map.Entry<String, List<Transaction>> entry : byMerchant.entrySet()) {
            String normKey = entry.getKey();
            List<Transaction> txns = entry.getValue();

            if (txns.size() < 2) continue;

            // Skip if already detected by known-subscription pass
            if (subscriptionRepository.findByNormalizedNameAndAccountId(normKey, account.getId()).isPresent())
                continue;

            if (!isConsistentAmount(txns)) continue;

            Subscription.SubscriptionFrequency freq = detectFrequency(txns);
            if (freq == null) continue;

            BigDecimal avg = calculateAvgAmount(txns);
            BigDecimal annual = calculateAnnualCost(avg, freq);
            LocalDate lastCharged = txns.stream()
                .map(Transaction::getTransactionDate).max(Comparator.naturalOrder()).orElse(LocalDate.now());

            Subscription s = Subscription.builder()
                .merchantName(txns.get(0).getMerchantName())
                .normalizedName(normKey)
                .amount(avg)
                .frequency(freq)
                .account(account)
                .firstSeenDate(txns.stream().map(Transaction::getTransactionDate).min(Comparator.naturalOrder()).orElse(LocalDate.now()))
                .lastChargedDate(lastCharged)
                .nextExpectedDate(calculateNextDate(lastCharged, freq))
                .timesCharged(txns.size())
                .annualCost(annual)
                .category("Subscriptions")
                .isActive(true)
                .build();

            found.add(subscriptionRepository.save(s));
            log.info("Pattern-based subscription: {} at ${} ({}) for account {}", normKey, avg, freq, account.getName());
        }
        return found;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String roughNormalize(String merchant) {
        if (merchant == null) return "";
        return merchant.toLowerCase()
            .replaceAll("\\*.*$", "")    // strip after asterisk
            .replaceAll("[^a-z0-9 ]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private boolean isConsistentAmount(List<Transaction> transactions) {
        BigDecimal avg = calculateAvgAmount(transactions);
        if (avg.compareTo(BigDecimal.ZERO) == 0) return false;
        BigDecimal tolerance = avg.multiply(BigDecimal.valueOf(0.10)); // 10% tolerance
        return transactions.stream().allMatch(t -> {
            BigDecimal diff = t.getAmount().subtract(avg).abs();
            return diff.compareTo(tolerance) <= 0;
        });
    }

    private BigDecimal calculateAvgAmount(List<Transaction> transactions) {
        return transactions.stream()
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);
    }

    private Subscription.SubscriptionFrequency detectFrequency(List<Transaction> transactions) {
        if (transactions.size() < 2) return null;
        List<LocalDate> dates = transactions.stream()
            .map(Transaction::getTransactionDate).sorted().collect(Collectors.toList());
        double avg = (double) ChronoUnit.DAYS.between(dates.get(0), dates.get(dates.size() - 1))
                     / (dates.size() - 1);
        if (avg >= 25 && avg <= 35) return Subscription.SubscriptionFrequency.MONTHLY;
        if (avg >= 85 && avg <= 100) return Subscription.SubscriptionFrequency.QUARTERLY;
        if (avg >= 350 && avg <= 380) return Subscription.SubscriptionFrequency.ANNUAL;
        if (avg >= 5 && avg <= 10) return Subscription.SubscriptionFrequency.WEEKLY;
        return null;
    }

    private BigDecimal calculateAnnualCost(BigDecimal amount, Subscription.SubscriptionFrequency freq) {
        return switch (freq) {
            case MONTHLY -> amount.multiply(BigDecimal.valueOf(12));
            case QUARTERLY -> amount.multiply(BigDecimal.valueOf(4));
            case WEEKLY -> amount.multiply(BigDecimal.valueOf(52));
            case ANNUAL -> amount;
        };
    }

    private LocalDate calculateNextDate(LocalDate lastCharged, Subscription.SubscriptionFrequency freq) {
        return switch (freq) {
            case MONTHLY -> lastCharged.plusMonths(1);
            case QUARTERLY -> lastCharged.plusMonths(3);
            case WEEKLY -> lastCharged.plusWeeks(1);
            case ANNUAL -> lastCharged.plusYears(1);
        };
    }

    @Transactional
    public void markDuplicates() {
        List<Subscription> all = subscriptionRepository.findPotentialDuplicates();
        Map<String, List<Subscription>> byName = all.stream()
            .collect(Collectors.groupingBy(Subscription::getNormalizedName));
        for (List<Subscription> group : byName.values()) {
            if (group.size() > 1) {
                Subscription primary = group.get(0);
                for (int i = 1; i < group.size(); i++) {
                    Subscription dup = group.get(i);
                    dup.setIsDuplicate(true);
                    dup.setDuplicateOf(primary);
                    subscriptionRepository.save(dup);
                }
            }
        }
    }
}

package com.financialguru.service;

import com.financialguru.model.Account;
import com.financialguru.model.Alert;
import com.financialguru.model.Subscription;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {

    private final AccountRepository accountRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AlertService alertService;
    private final BudgetService budgetService;
    private final InsightEngineService insightEngineService;
    private final NetWorthService netWorthService;

    // Run daily at 8 AM
    @Scheduled(cron = "0 0 8 * * *")
    public void checkDueDates() {
        log.info("Running due date check...");
        LocalDate today = LocalDate.now();
        List<Account> accounts = accountRepository.findAccountsWithPaymentDueDays();

        for (Account account : accounts) {
            if (account.getPaymentDueDay() == null) continue;

            LocalDate dueDate = today.withDayOfMonth(
                Math.min(account.getPaymentDueDay(), today.lengthOfMonth()));
            if (dueDate.isBefore(today)) dueDate = dueDate.plusMonths(1);

            long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);

            if (daysUntilDue == 7 || daysUntilDue == 3 || daysUntilDue == 1) {
                Alert.AlertSeverity severity = daysUntilDue == 1
                    ? Alert.AlertSeverity.HIGH : Alert.AlertSeverity.MEDIUM;

                alertService.createAlert(
                    Alert.AlertType.DUE_DATE,
                    severity,
                    "Payment Due in " + daysUntilDue + " day" + (daysUntilDue == 1 ? "" : "s"),
                    String.format("%s payment due on %s. Balance: $%.2f, Min payment: $%.2f",
                        account.getName(), dueDate,
                        account.getCurrentBalance() != null ? account.getCurrentBalance() : BigDecimal.ZERO,
                        account.getMinPayment() != null ? account.getMinPayment() : BigDecimal.ZERO),
                    account, null, null
                );
                log.info("Created due date alert for {} ({} days)", account.getName(), daysUntilDue);
            }
        }
    }

    // Run daily at 9 AM
    @Scheduled(cron = "0 0 9 * * *")
    public void checkPromoAprExpiry() {
        log.info("Running promo APR expiry check...");
        LocalDate today = LocalDate.now();
        List<Account> accounts = accountRepository.findAccountsWithPromoAprExpiring();

        for (Account account : accounts) {
            if (account.getPromoAprEndDate() == null) continue;

            long daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(
                today, account.getPromoAprEndDate());

            if (daysUntilExpiry == 30 || daysUntilExpiry == 14 || daysUntilExpiry == 7) {
                Alert.AlertSeverity severity = daysUntilExpiry <= 7
                    ? Alert.AlertSeverity.HIGH : Alert.AlertSeverity.MEDIUM;

                alertService.createAlert(
                    Alert.AlertType.APR_EXPIRY,
                    severity,
                    "Promo APR Expiring in " + daysUntilExpiry + " days",
                    String.format("%s promo APR (%.2f%%) expires on %s. Regular APR %.2f%% will apply. Balance: $%.2f",
                        account.getName(), account.getPromoApr(), account.getPromoAprEndDate(),
                        account.getApr() != null ? account.getApr() : BigDecimal.ZERO,
                        account.getCurrentBalance() != null ? account.getCurrentBalance() : BigDecimal.ZERO),
                    account, null,
                    "Consider paying down the balance before the promo APR expires to avoid higher interest charges."
                );
                log.info("Created APR expiry alert for {} ({} days)", account.getName(), daysUntilExpiry);
            }
        }
    }

    // Run weekly on Sunday at 10 AM
    @Scheduled(cron = "0 0 10 * * SUN")
    public void checkHighUtilization() {
        log.info("Running high utilization check...");
        List<Account> accounts = accountRepository.findByTypeOrderByNameAsc(Account.AccountType.CREDIT_CARD);

        for (Account account : accounts) {
            if (account.getCreditLimit() == null || account.getCurrentBalance() == null) continue;
            if (account.getCreditLimit().compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal utilization = account.getCurrentBalance()
                .divide(account.getCreditLimit(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

            if (utilization.compareTo(BigDecimal.valueOf(30)) > 0) {
                Alert.AlertSeverity severity = utilization.compareTo(BigDecimal.valueOf(70)) > 0
                    ? Alert.AlertSeverity.HIGH : Alert.AlertSeverity.MEDIUM;

                alertService.createAlert(
                    Alert.AlertType.HIGH_UTILIZATION,
                    severity,
                    "High Credit Utilization",
                    String.format("%s is at %.1f%% utilization ($%.2f / $%.2f). High utilization hurts credit score.",
                        account.getName(), utilization, account.getCurrentBalance(), account.getCreditLimit()),
                    account, null,
                    "Keep utilization below 30% for a healthy credit score. Consider making an extra payment."
                );
            }
        }
    }

    // Check upcoming subscription charges — daily at 10 AM
    @Scheduled(cron = "0 0 10 * * *")
    public void checkUpcomingSubscriptions() {
        LocalDate today = LocalDate.now();
        LocalDate upcoming = today.plusDays(3);

        List<Subscription> subscriptions = subscriptionRepository.findByIsActiveTrueOrderByAnnualCostDesc();
        for (Subscription sub : subscriptions) {
            if (sub.getNextExpectedDate() != null &&
                !sub.getNextExpectedDate().isBefore(today) &&
                !sub.getNextExpectedDate().isAfter(upcoming)) {

                alertService.createAlert(
                    Alert.AlertType.SUBSCRIPTION,
                    Alert.AlertSeverity.LOW,
                    "Upcoming Subscription Charge",
                    String.format("%s ($%.2f) expected on %s",
                        sub.getMerchantName(), sub.getAmount(), sub.getNextExpectedDate()),
                    sub.getAccount(), null, null
                );
            }
        }
    }

    // Check budgets daily at 8 PM
    @Scheduled(cron = "0 0 20 * * ?")
    public void checkBudgets() {
        log.info("Running budget check...");
        budgetService.checkAndAlertBudgets();
    }

    // Run insight engine daily at 2 AM
    @Scheduled(cron = "0 0 2 * * ?")
    public void runInsightEngine() {
        log.info("Running insight engine...");
        insightEngineService.runAll();
    }

    // Capture monthly net worth snapshot on the 1st of every month at 1 AM
    @Scheduled(cron = "0 0 1 1 * ?")
    public void captureMonthlyNetWorth() {
        log.info("Capturing monthly net worth snapshot...");
        netWorthService.captureSnapshot();
    }
}

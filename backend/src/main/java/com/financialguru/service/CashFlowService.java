package com.financialguru.service;

import com.financialguru.dto.response.CashFlowResponse;
import com.financialguru.dto.response.CashFlowResponse.CashFlowEvent;
import com.financialguru.model.Account;
import com.financialguru.model.FinancialProfile;
import com.financialguru.model.Subscription;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashFlowService {

    private final AccountRepository accountRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final FinancialProfileService financialProfileService;

    public CashFlowResponse getCashFlowCalendar(int year, int month) {
        FinancialProfile profile = financialProfileService.getOrCreateProfile();
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        List<Account> accounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        BigDecimal startBalance = accounts.stream()
            .filter(a -> a.getType() == Account.AccountType.CHECKING && a.getCurrentBalance() != null)
            .map(Account::getCurrentBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CashFlowEvent> events = new ArrayList<>();

        if (profile.getMonthlyIncome() != null && profile.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
            String freq = profile.getPayFrequency() != null ? profile.getPayFrequency() : "MONTHLY";
            switch (freq) {
                case "MONTHLY" -> events.add(new CashFlowEvent(
                    firstDay, "INCOME", "Monthly Salary", profile.getMonthlyIncome(), null, false));
                case "BIWEEKLY" -> {
                    LocalDate pay = firstDay;
                    // 26 biweekly pays/year, not 24 — each = monthlyIncome × 12 / 26
                    BigDecimal biweekly = profile.getMonthlyIncome()
                        .multiply(BigDecimal.valueOf(12))
                        .divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
                    while (!pay.isAfter(lastDay)) {
                        events.add(new CashFlowEvent(pay, "INCOME", "Biweekly Pay", biweekly, null, false));
                        pay = pay.plusDays(14);
                    }
                }
                case "WEEKLY" -> {
                    LocalDate pay = firstDay;
                    // 52 weekly pays/year, not 48 — each = monthlyIncome × 12 / 52
                    BigDecimal weekly = profile.getMonthlyIncome()
                        .multiply(BigDecimal.valueOf(12))
                        .divide(BigDecimal.valueOf(52), 2, RoundingMode.HALF_UP);
                    while (!pay.isAfter(lastDay)) {
                        events.add(new CashFlowEvent(pay, "INCOME", "Weekly Pay", weekly, null, false));
                        pay = pay.plusDays(7);
                    }
                }
                default -> events.add(new CashFlowEvent(
                    firstDay, "INCOME", "Monthly Salary", profile.getMonthlyIncome(), null, false));
            }
        }

        for (Account a : accounts) {
            if (a.getType() != Account.AccountType.CREDIT_CARD) continue;
            if (a.getPaymentDueDay() != null) {
                int day = Math.min(a.getPaymentDueDay(), firstDay.lengthOfMonth());
                LocalDate dueDate = LocalDate.of(year, month, day);
                BigDecimal payment = a.getMinPayment() != null ? a.getMinPayment() : BigDecimal.valueOf(25);
                events.add(new CashFlowEvent(
                    dueDate, "PAYMENT", a.getName() + " Payment", payment.negate(), null, false));
            }
        }

        List<Subscription> subs = subscriptionRepository.findByIsActiveTrueOrderByAnnualCostDesc();
        for (Subscription sub : subs) {
            if (sub.getNextExpectedDate() != null
                && !sub.getNextExpectedDate().isBefore(firstDay)
                && !sub.getNextExpectedDate().isAfter(lastDay)) {
                BigDecimal amt = sub.getAmount() != null ? sub.getAmount().negate() : BigDecimal.ZERO;
                events.add(new CashFlowEvent(
                    sub.getNextExpectedDate(), "SUBSCRIPTION", sub.getMerchantName(), amt, null, false));
            }
        }

        events.sort(Comparator.comparing(CashFlowEvent::getDate));

        BigDecimal running = startBalance;
        for (CashFlowEvent e : events) {
            running = running.add(e.getAmount());
            e.setRunningBalance(running);
            e.setIsDangerDay(running.compareTo(new BigDecimal("500")) < 0);
        }

        return CashFlowResponse.builder()
            .year(year)
            .month(month)
            .startingBalance(startBalance)
            .events(events)
            .build();
    }
}

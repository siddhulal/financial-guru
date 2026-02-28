package com.financialguru.service;

import com.financialguru.dto.response.DebtPayoffResponse;
import com.financialguru.dto.response.DebtPayoffResponse.CardPayoffDetail;
import com.financialguru.dto.response.DebtPayoffResponse.PayoffStrategy;
import com.financialguru.dto.response.WhatIfDataPoint;
import com.financialguru.model.Account;
import com.financialguru.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtPayoffService {

    private final AccountRepository accountRepository;

    public DebtPayoffResponse calculatePayoff(BigDecimal extraMonthlyPayment) {
        List<Account> creditCards = accountRepository.findByTypeOrderByNameAsc(Account.AccountType.CREDIT_CARD)
            .stream()
            .filter(a -> a.getCurrentBalance() != null && a.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0)
            .collect(Collectors.toList());

        BigDecimal totalDebt = creditCards.stream()
            .map(Account::getCurrentBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        PayoffStrategy avalanche = simulatePayoff(creditCards, extraMonthlyPayment, "AVALANCHE");
        PayoffStrategy snowball = simulatePayoff(creditCards, extraMonthlyPayment, "SNOWBALL");

        return DebtPayoffResponse.builder()
            .extraPayment(extraMonthlyPayment)
            .totalCurrentDebt(totalDebt)
            .avalanche(avalanche)
            .snowball(snowball)
            .build();
    }

    private PayoffStrategy simulatePayoff(List<Account> cards, BigDecimal extra, String strategy) {
        List<CardState> states = cards.stream().map(CardState::new).collect(Collectors.toList());

        if ("AVALANCHE".equals(strategy)) {
            states.sort((a, b) -> b.apr.compareTo(a.apr));
        } else {
            states.sort(Comparator.comparing(a -> a.balance));
        }

        BigDecimal totalInterest = BigDecimal.ZERO;
        int months = 0;
        LocalDate payoffDate = LocalDate.now();

        while (states.stream().anyMatch(s -> s.balance.compareTo(BigDecimal.ZERO) > 0) && months < 360) {
            months++;
            for (CardState cs : states) {
                if (cs.balance.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal monthlyRate = cs.apr.divide(BigDecimal.valueOf(1200), 8, RoundingMode.HALF_UP);
                BigDecimal interest = cs.balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
                cs.balance = cs.balance.add(interest);
                cs.interestPaid = cs.interestPaid.add(interest);
                totalInterest = totalInterest.add(interest);
                BigDecimal minPay = cs.minPayment != null ? cs.minPayment
                    : cs.balance.multiply(new BigDecimal("0.02")).max(new BigDecimal("25"));
                minPay = minPay.min(cs.balance);
                cs.balance = cs.balance.subtract(minPay);
            }

            BigDecimal remaining = extra;
            for (CardState cs : states) {
                if (cs.balance.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal payment = remaining.min(cs.balance);
                cs.balance = cs.balance.subtract(payment);
                remaining = remaining.subtract(payment);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            }

            for (CardState cs : states) {
                if (cs.balance.compareTo(BigDecimal.ZERO) <= 0 && cs.payoffDate == null) {
                    cs.payoffDate = LocalDate.now().plusMonths(months);
                }
            }
        }
        payoffDate = LocalDate.now().plusMonths(months);

        List<CardPayoffDetail> cardDetails = new ArrayList<>();
        int order = 1;
        for (CardState cs : states) {
            cardDetails.add(CardPayoffDetail.builder()
                .accountId(cs.id)
                .accountName(cs.name)
                .currentBalance(cs.originalBalance)
                .apr(cs.apr)
                .minPayment(cs.minPayment)
                .payoffDate(cs.payoffDate != null ? cs.payoffDate : payoffDate)
                .interestPaid(cs.interestPaid)
                .payoffOrder(order++)
                .build());
        }

        BigDecimal totalPaid = states.stream()
            .map(s -> s.originalBalance.add(s.interestPaid))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PayoffStrategy.builder()
            .strategy(strategy)
            .totalMonths(months)
            .payoffDate(payoffDate)
            .totalInterest(totalInterest)
            .totalPaid(totalPaid)
            .cardOrder(cardDetails)
            .build();
    }

    public List<WhatIfDataPoint> calculateWhatIfRange() {
        BigDecimal[] extraPayments = {
            BigDecimal.ZERO,
            new BigDecimal("50"),
            new BigDecimal("100"),
            new BigDecimal("200"),
            new BigDecimal("300"),
            new BigDecimal("500"),
            new BigDecimal("750"),
            new BigDecimal("1000"),
            new BigDecimal("1500"),
            new BigDecimal("2000")
        };

        List<WhatIfDataPoint> points = new ArrayList<>();
        for (BigDecimal extra : extraPayments) {
            DebtPayoffResponse r = calculatePayoff(extra);
            points.add(WhatIfDataPoint.builder()
                .extraPayment(extra)
                .avalancheMonths(r.getAvalanche().getTotalMonths())
                .avalancheTotalInterest(r.getAvalanche().getTotalInterest())
                .avalanchePayoffDate(r.getAvalanche().getPayoffDate())
                .snowballMonths(r.getSnowball().getTotalMonths())
                .build());
        }
        return points;
    }

    private static class CardState {
        UUID id;
        String name;
        BigDecimal balance;
        BigDecimal originalBalance;
        BigDecimal apr;
        BigDecimal minPayment;
        BigDecimal interestPaid = BigDecimal.ZERO;
        LocalDate payoffDate;

        CardState(Account a) {
            this.id = a.getId();
            this.name = a.getName();
            this.balance = a.getCurrentBalance();
            this.originalBalance = a.getCurrentBalance();
            this.apr = a.getApr() != null ? a.getApr() : new BigDecimal("20");
            this.minPayment = a.getMinPayment();
        }
    }
}

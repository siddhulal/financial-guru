package com.financialguru.service;

import com.financialguru.dto.response.FireCalculatorResponse;
import com.financialguru.model.FinancialProfile;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class FireCalculatorService {

    private final FinancialProfileService financialProfileService;
    private final TransactionRepository transactionRepository;

    public FireCalculatorResponse calculate(
            BigDecimal age,
            BigDecimal targetRetirementAge,
            BigDecimal currentInvestments,
            BigDecimal monthlyExpenses) {

        FinancialProfile profile = financialProfileService.getOrCreateProfile();
        BigDecimal monthlyIncome = profile.getMonthlyIncome() != null
                ? profile.getMonthlyIncome() : BigDecimal.ZERO;

        // Calculate actual monthly expenses from last 3 months if not provided
        if (monthlyExpenses == null || monthlyExpenses.compareTo(BigDecimal.ZERO) == 0) {
            LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
            BigDecimal spend3M = transactionRepository.sumAllSpending(threeMonthsAgo, LocalDate.now());
            monthlyExpenses = spend3M != null
                    ? spend3M.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(3000);
        }

        BigDecimal annualExpenses = monthlyExpenses.multiply(BigDecimal.valueOf(12));
        BigDecimal fiNumber = annualExpenses.multiply(BigDecimal.valueOf(25)); // 4% rule

        // Calculate current savings rate
        BigDecimal monthlySavings = monthlyIncome.subtract(monthlyExpenses).max(BigDecimal.ZERO);
        BigDecimal savingsRate = monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                ? monthlySavings.divide(monthlyIncome, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Years to FIRE using compound growth formula
        double annualReturn = 0.07; // 7% expected return
        double annualSavings = monthlySavings.doubleValue() * 12;
        double target = fiNumber.doubleValue();
        double current = currentInvestments != null ? currentInvestments.doubleValue() : 0;

        // FV = PV*(1+r)^n + PMT*((1+r)^n - 1)/r — solve for n using iteration
        double yearsToFire = 0;
        if (annualSavings <= 0 && current >= target) {
            yearsToFire = 0;
        } else if (annualSavings <= 0) {
            yearsToFire = 360; // Can't retire
        } else {
            for (int n = 0; n <= 60; n++) {
                double fv = current * Math.pow(1 + annualReturn, n)
                        + annualSavings * (Math.pow(1 + annualReturn, n) - 1) / annualReturn;
                if (fv >= target) {
                    yearsToFire = n;
                    break;
                }
                if (n == 60) yearsToFire = 60;
            }
        }

        LocalDate fireDate = LocalDate.now().plusYears((long) yearsToFire);

        // Monthly savings gap (how much more per month needed to retire by target age)
        BigDecimal monthlySavingsGap = BigDecimal.ZERO;
        if (targetRetirementAge != null && age != null) {
            double yearsAvailable = targetRetirementAge.subtract(age).doubleValue();
            if (yearsAvailable > 0 && current < target) {
                double monthsAvailable = yearsAvailable * 12;
                double monthlyRate = annualReturn / 12;
                double required = (target - current * Math.pow(1 + annualReturn, yearsAvailable))
                        * monthlyRate / (Math.pow(1 + monthlyRate, monthsAvailable) - 1);
                monthlySavingsGap = BigDecimal.valueOf(
                        Math.max(0, required - monthlySavings.doubleValue()))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Year-by-year projections (up to 40 years)
        List<FireCalculatorResponse.YearProjection> projections = new ArrayList<>();
        double portfolio = current;
        for (int y = 0; y <= Math.min(40, (int) yearsToFire + 5); y++) {
            projections.add(new FireCalculatorResponse.YearProjection(
                    LocalDate.now().getYear() + y,
                    BigDecimal.valueOf(portfolio).setScale(0, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(annualSavings).setScale(0, RoundingMode.HALF_UP)));
            portfolio = portfolio * (1 + annualReturn) + annualSavings;
        }

        // Monte Carlo simulation: 100 runs, 40 years
        Random rand = new Random(42);
        List<double[]> simRuns = new ArrayList<>();
        for (int sim = 0; sim < 100; sim++) {
            double val = current;
            double[] yearlyVals = new double[41];
            yearlyVals[0] = val;
            for (int y = 1; y <= 40; y++) {
                double annualRet = 0.07 + (rand.nextGaussian() * 0.15); // mean 7%, std 15%
                val = val * (1 + annualRet) + annualSavings;
                yearlyVals[y] = Math.max(0, val);
            }
            simRuns.add(yearlyVals);
        }

        // Compute percentiles
        List<BigDecimal> p10 = new ArrayList<>();
        List<BigDecimal> p50 = new ArrayList<>();
        List<BigDecimal> p90 = new ArrayList<>();
        for (int y = 0; y <= 40; y++) {
            final int yr = y;
            double[] vals = simRuns.stream().mapToDouble(r -> r[yr]).sorted().toArray();
            // Standard nearest-rank percentile: index = ceil(N * p) - 1 (0-based)
            p10.add(BigDecimal.valueOf(vals[Math.max(0, (int)Math.ceil(vals.length * 0.10) - 1)]).setScale(0, RoundingMode.HALF_UP));
            p50.add(BigDecimal.valueOf(vals[Math.max(0, (int)Math.ceil(vals.length * 0.50) - 1)]).setScale(0, RoundingMode.HALF_UP));
            p90.add(BigDecimal.valueOf(vals[Math.min(vals.length - 1, (int)Math.ceil(vals.length * 0.90) - 1)]).setScale(0, RoundingMode.HALF_UP));
        }

        return FireCalculatorResponse.builder()
                .fiNumber(fiNumber.setScale(0, RoundingMode.HALF_UP))
                .currentSavings(BigDecimal.valueOf(current).setScale(0, RoundingMode.HALF_UP))
                .annualExpenses(annualExpenses)
                .monthlySavings(monthlySavings)
                .yearsToFire(yearsToFire)
                .fireDate(fireDate)
                .savingsRate(savingsRate)
                .monthlySavingsGap(monthlySavingsGap)
                .projections(projections)
                .monteCarloP10(p10)
                .monteCarloP50(p50)
                .monteCarloP90(p90)
                .build();
    }
}

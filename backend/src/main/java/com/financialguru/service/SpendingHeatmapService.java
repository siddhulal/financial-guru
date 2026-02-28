package com.financialguru.service;

import com.financialguru.dto.response.DuplicateTransactionGroup;
import com.financialguru.dto.response.MerchantTrendResponse;
import com.financialguru.dto.response.SpendingHeatmapResponse;
import com.financialguru.dto.response.TransactionResponse;
import com.financialguru.model.Transaction;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpendingHeatmapService {

    private final TransactionRepository transactionRepository;

    public SpendingHeatmapResponse getHeatmap(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31).isBefore(LocalDate.now())
                ? LocalDate.of(year, 12, 31) : LocalDate.now();

        List<Object[]> rows = transactionRepository.findDailySpendingTotals(start, end);
        Map<LocalDate, Object[]> byDate = new LinkedHashMap<>();
        for (Object[] row : rows) {
            byDate.put((LocalDate) row[0], row);
        }

        BigDecimal maxDaily = rows.stream()
                .map(r -> (BigDecimal) r[1])
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
        BigDecimal totalAnnual = rows.stream()
                .map(r -> (BigDecimal) r[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SpendingHeatmapResponse.HeatmapDay> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Object[] row = byDate.get(d);
            BigDecimal spend = row != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
            int count = row != null ? ((Long) row[2]).intValue() : 0;
            // Intensity 0-4: 0=none, 1=low, 2=medium, 3=high, 4=very high
            int intensity = 0;
            if (spend.compareTo(BigDecimal.ZERO) > 0 && maxDaily.compareTo(BigDecimal.ZERO) > 0) {
                double pct = spend.divide(maxDaily, 4, RoundingMode.HALF_UP).doubleValue();
                intensity = pct < 0.2 ? 1 : pct < 0.4 ? 2 : pct < 0.7 ? 3 : 4;
            }
            days.add(new SpendingHeatmapResponse.HeatmapDay(d, spend, count, intensity));
        }

        return SpendingHeatmapResponse.builder()
                .year(year)
                .days(days)
                .maxDailySpend(maxDaily)
                .totalAnnualSpend(totalAnnual)
                .build();
    }

    public MerchantTrendResponse getMerchantTrend(String merchant) {
        LocalDate since = LocalDate.now().minusMonths(12).withDayOfMonth(1);
        List<Object[]> rows = transactionRepository.findMerchantMonthlyTrend(merchant, since);

        List<MerchantTrendResponse.MonthlyAmount> months = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] row : rows) {
            BigDecimal amt = (BigDecimal) row[1];
            months.add(new MerchantTrendResponse.MonthlyAmount((String) row[0], amt));
            total = total.add(amt);
        }

        BigDecimal avg = months.isEmpty() ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(months.size()), 2, RoundingMode.HALF_UP);

        // Trend: compare last 3 months avg vs prior 3 months avg
        String trend = "STABLE";
        if (months.size() >= 6) {
            BigDecimal recent = months.subList(months.size() - 3, months.size()).stream()
                    .map(MerchantTrendResponse.MonthlyAmount::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            BigDecimal prior = months.subList(months.size() - 6, months.size() - 3).stream()
                    .map(MerchantTrendResponse.MonthlyAmount::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            if (prior.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change = recent.subtract(prior).divide(prior, 4, RoundingMode.HALF_UP);
                trend = change.compareTo(new BigDecimal("0.1")) > 0 ? "INCREASING"
                        : change.compareTo(new BigDecimal("-0.1")) < 0 ? "DECREASING" : "STABLE";
            }
        }

        return MerchantTrendResponse.builder()
                .merchantName(merchant)
                .months(months)
                .totalAnnual(total)
                .avgMonthly(avg)
                .trend(trend)
                .build();
    }

    public List<DuplicateTransactionGroup> findDuplicates() {
        LocalDate since = LocalDate.now().minusDays(30);
        List<Transaction> txns = transactionRepository.findRecentUnflaggedDebits(since);

        // Group by merchantName + amount, find groups where same merchant+amount appear multiple times
        Map<String, List<Transaction>> groups = new LinkedHashMap<>();
        for (Transaction t : txns) {
            if (t.getMerchantName() == null) continue;
            String key = t.getMerchantName().toLowerCase() + "|" + t.getAmount();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        List<DuplicateTransactionGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<Transaction>> e : groups.entrySet()) {
            List<Transaction> group = e.getValue();
            if (group.size() < 2) continue;
            // Check if any two txns are within 7 days of each other
            boolean hasDuplicate = false;
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    long daysBetween = Math.abs(ChronoUnit.DAYS.between(
                            group.get(i).getTransactionDate(),
                            group.get(j).getTransactionDate()));
                    if (daysBetween <= 7) {
                        hasDuplicate = true;
                        break;
                    }
                }
                if (hasDuplicate) break;
            }
            if (!hasDuplicate) continue;

            Transaction first = group.get(0);
            result.add(DuplicateTransactionGroup.builder()
                    .merchantName(first.getMerchantName())
                    .amount(first.getAmount())
                    .transactions(group.stream()
                            .map(TransactionResponse::from)
                            .collect(Collectors.toList()))
                    .withinDays(7)
                    .build());
        }
        return result;
    }
}

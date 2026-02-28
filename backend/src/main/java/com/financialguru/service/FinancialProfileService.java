package com.financialguru.service;

import com.financialguru.dto.request.FinancialProfileRequest;
import com.financialguru.model.FinancialProfile;
import com.financialguru.model.Transaction;
import com.financialguru.repository.FinancialProfileRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialProfileService {

    private final FinancialProfileRepository financialProfileRepository;
    private final TransactionRepository transactionRepository;

    public FinancialProfile getOrCreateProfile() {
        return financialProfileRepository.findFirst()
            .orElseGet(() -> {
                FinancialProfile p = FinancialProfile.builder()
                    .incomeSource("MANUAL")
                    .payFrequency("MONTHLY")
                    .emergencyFundTargetMonths(6)
                    .build();
                return financialProfileRepository.save(p);
            });
    }

    @Transactional
    public FinancialProfile updateProfile(FinancialProfileRequest req) {
        FinancialProfile p = getOrCreateProfile();
        if (req.getMonthlyIncome() != null) p.setMonthlyIncome(req.getMonthlyIncome());
        if (req.getIncomeSource() != null) p.setIncomeSource(req.getIncomeSource());
        if (req.getPayFrequency() != null) p.setPayFrequency(req.getPayFrequency());
        if (req.getEmergencyFundTargetMonths() != null) p.setEmergencyFundTargetMonths(req.getEmergencyFundTargetMonths());
        if (req.getNotes() != null) p.setNotes(req.getNotes());
        if (req.getAge() != null) p.setAge(req.getAge());
        if (req.getTargetRetirementAge() != null) p.setTargetRetirementAge(req.getTargetRetirementAge());
        if (req.getCurrentInvestments() != null) p.setCurrentInvestments(req.getCurrentInvestments());
        return financialProfileRepository.save(p);
    }

    public BigDecimal detectMonthlyIncome() {
        LocalDate since = LocalDate.now().minusMonths(3);
        List<Transaction> incomeTransactions = transactionRepository.findPotentialIncomeTransactions(
            new BigDecimal("500"), since);

        if (incomeTransactions.isEmpty()) return BigDecimal.ZERO;

        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        for (Transaction t : incomeTransactions) {
            String key = t.getTransactionDate().getYear() + "-" + t.getTransactionDate().getMonthValue();
            byMonth.merge(key, t.getAmount(), BigDecimal::add);
        }

        List<BigDecimal> monthly = new ArrayList<>(byMonth.values());
        Collections.sort(monthly);
        return monthly.get(monthly.size() / 2);
    }
}

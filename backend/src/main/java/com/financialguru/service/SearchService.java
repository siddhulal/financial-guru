package com.financialguru.service;

import com.financialguru.dto.response.AccountResponse;
import com.financialguru.dto.response.SearchResult;
import com.financialguru.dto.response.TransactionResponse;
import com.financialguru.model.Account;
import com.financialguru.model.Transaction;
import com.financialguru.repository.AccountRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public SearchResult search(String query) {
        if (query == null || query.isBlank()) {
            return SearchResult.builder()
                    .query(query)
                    .transactions(List.of())
                    .accounts(List.of())
                    .merchants(List.of())
                    .totalResults(0)
                    .build();
        }

        String q = query.trim();
        Pageable top5 = PageRequest.of(0, 5);

        List<Transaction> txns = transactionRepository.searchTransactions(q, top5);
        List<Account> accounts = accountRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .filter(a -> a.getName().toLowerCase().contains(q.toLowerCase())
                        || (a.getInstitution() != null
                                && a.getInstitution().toLowerCase().contains(q.toLowerCase())))
                .limit(5)
                .collect(Collectors.toList());

        // Top merchants matching query
        List<String> merchants = transactionRepository.findAllTopMerchants(
                        LocalDate.now().minusMonths(12), LocalDate.now())
                .stream()
                .map(r -> (String) r[0])
                .filter(m -> m.toLowerCase().contains(q.toLowerCase()))
                .limit(5)
                .collect(Collectors.toList());

        int total = txns.size() + accounts.size() + merchants.size();

        return SearchResult.builder()
                .query(q)
                .transactions(txns.stream().map(TransactionResponse::from).collect(Collectors.toList()))
                .accounts(accounts.stream().map(AccountResponse::from).collect(Collectors.toList()))
                .merchants(merchants)
                .totalResults(total)
                .build();
    }
}

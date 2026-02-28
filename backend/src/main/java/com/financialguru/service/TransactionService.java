package com.financialguru.service;

import com.financialguru.dto.request.TransactionFilterRequest;
import com.financialguru.dto.response.TransactionResponse;
import com.financialguru.model.Transaction;
import com.financialguru.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public Page<TransactionResponse> getTransactions(TransactionFilterRequest filter) {
        Pageable pageable = PageRequest.of(
            filter.getPage(), filter.getSize(),
            Sort.by(Sort.Direction.DESC, "transactionDate")
        );
        Specification<Transaction> spec = buildSpec(filter);
        return transactionRepository.findAll(spec, pageable).map(TransactionResponse::from);
    }

    private Specification<Transaction> buildSpec(TransactionFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (f.getAccountId() != null) {
                predicates.add(cb.equal(root.get("account").get("id"), f.getAccountId()));
            }
            if (f.getCategory() != null && !f.getCategory().isBlank()) {
                predicates.add(cb.equal(root.get("category"), f.getCategory()));
            }
            if (f.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), f.getStartDate()));
            }
            if (f.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), f.getEndDate()));
            }
            if (f.getMinAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), f.getMinAmount()));
            }
            if (f.getMaxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), f.getMaxAmount()));
            }
            if (f.getSearch() != null && !f.getSearch().isBlank()) {
                String pattern = "%" + f.getSearch().toLowerCase() + "%";
                Predicate byMerchant = cb.like(cb.lower(root.get("merchantName")), pattern);
                Predicate byDescription = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(byMerchant, byDescription));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public TransactionResponse getTransaction(UUID id) {
        Transaction t = transactionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + id));
        return TransactionResponse.from(t);
    }

    public List<TransactionResponse> getAnomalies() {
        return transactionRepository.findByIsFlaggedTrueOrderByTransactionDateDesc()
            .stream()
            .map(TransactionResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional
    public TransactionResponse updateTransaction(UUID id, Map<String, Object> updates) {
        Transaction t = transactionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + id));

        if (updates.containsKey("category")) {
            t.setCategory((String) updates.get("category"));
        }
        if (updates.containsKey("notes")) {
            t.setNotes((String) updates.get("notes"));
        }
        if (updates.containsKey("isFlagged")) {
            t.setIsFlagged((Boolean) updates.get("isFlagged"));
        }
        if (updates.containsKey("flagReason")) {
            t.setFlagReason((String) updates.get("flagReason"));
        }

        return TransactionResponse.from(transactionRepository.save(t));
    }

    public List<TransactionResponse> getByStatementId(UUID statementId) {
        return transactionRepository.findByStatementIdOrderByTransactionDateDesc(statementId)
            .stream()
            .map(TransactionResponse::from)
            .collect(Collectors.toList());
    }
}

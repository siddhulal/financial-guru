package com.financialguru.service;

import com.financialguru.dto.response.AnalysisResponse;
import com.financialguru.model.AnalysisResult;
import com.financialguru.model.Statement;
import com.financialguru.model.Transaction;
import com.financialguru.repository.AnalysisResultRepository;
import com.financialguru.repository.StatementRepository;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final AnalysisResultRepository analysisResultRepository;
    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final OllamaService ollamaService;

    @Value("${app.ollama.model:llama3.1:13b}")
    private String model;

    @Async
    @Transactional
    public void runFullAnalysis(UUID statementId) {
        log.info("Starting full AI analysis for statement {}", statementId);

        Statement statement = statementRepository.findById(statementId)
            .orElseThrow(() -> new RuntimeException("Statement not found: " + statementId));

        List<Transaction> transactions = transactionRepository
            .findByStatementIdOrderByTransactionDateDesc(statementId);

        if (transactions.isEmpty()) {
            log.warn("No transactions found for statement {}", statementId);
            return;
        }

        // 1. Categorization
        runCategorizationAnalysis(statement, transactions);

        // 2. Anomaly detection
        runAnomalyAnalysis(statement, transactions);

        // 3. Summary
        runSummaryAnalysis(statement, transactions);

        log.info("Completed AI analysis for statement {}", statementId);
    }

    private void runCategorizationAnalysis(Statement statement, List<Transaction> transactions) {
        long start = Instant.now().toEpochMilli();

        List<Map<String, Object>> txData = transactions.stream()
            .filter(t -> t.getCategory() == null)
            .limit(100) // Batch limit
            .map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.getId().toString());
                m.put("merchant", t.getMerchantName() != null ? t.getMerchantName() : t.getDescription());
                m.put("amount", t.getAmount());
                m.put("date", t.getTransactionDate().toString());
                return m;
            })
            .collect(Collectors.toList());

        String prompt = """
            Categorize these financial transactions. For each transaction, assign:
            - category: one of (Dining, Groceries, Shopping, Travel, Gas, Entertainment, Utilities, Healthcare,
              Subscriptions, Education, Personal Care, Home, Automotive, Insurance, Investments, Fees, Other)
            - subcategory: more specific (e.g. "Fast Food", "Streaming", "Clothing")
            - normalizedMerchant: clean merchant name

            Transactions:
            """ + toJson(txData) + """

            Return JSON: {"categorizations": [{"id": "...", "category": "...", "subcategory": "...", "normalizedMerchant": "..."}]}
            """;

        try {
            Map<String, Object> result = ollamaService.chatJson(prompt);
            long processingMs = Instant.now().toEpochMilli() - start;

            // Apply categorizations to transactions
            if (result.containsKey("categorizations")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cats = (List<Map<String, Object>>) result.get("categorizations");
                Map<String, Map<String, Object>> catById = cats.stream()
                    .collect(Collectors.toMap(c -> (String) c.get("id"), c -> c));

                transactions.forEach(t -> {
                    Map<String, Object> cat = catById.get(t.getId().toString());
                    if (cat != null) {
                        t.setCategory((String) cat.get("category"));
                        t.setSubcategory((String) cat.get("subcategory"));
                        if (t.getMerchantName() == null && cat.containsKey("normalizedMerchant")) {
                            t.setMerchantName((String) cat.get("normalizedMerchant"));
                        }
                    }
                });
                transactionRepository.saveAll(transactions);
            }

            saveAnalysisResult(statement, AnalysisResult.AnalysisType.CATEGORIZATION, result, processingMs);
        } catch (Exception e) {
            log.error("Categorization analysis failed: {}", e.getMessage());
        }
    }

    private void runAnomalyAnalysis(Statement statement, List<Transaction> transactions) {
        long start = Instant.now().toEpochMilli();

        List<Map<String, Object>> txData = transactions.stream()
            .limit(50)
            .map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("merchant", t.getMerchantName() != null ? t.getMerchantName() : t.getDescription());
                m.put("amount", t.getAmount());
                m.put("date", t.getTransactionDate().toString());
                m.put("category", t.getCategory());
                return m;
            })
            .collect(Collectors.toList());

        String prompt = """
            Analyze these transactions for anomalies, unusual patterns, or concerning activity.
            Look for: unusual amounts, suspicious merchants, patterns that don't match normal spending.

            Transactions:
            """ + toJson(txData) + """

            Return JSON: {"anomalies": [{"merchant": "...", "amount": ..., "reason": "...", "severity": "LOW|MEDIUM|HIGH"}],
                         "summary": "brief overall assessment"}
            """;

        try {
            Map<String, Object> result = ollamaService.chatJson(prompt);
            long processingMs = Instant.now().toEpochMilli() - start;
            saveAnalysisResult(statement, AnalysisResult.AnalysisType.ANOMALY, result, processingMs);
        } catch (Exception e) {
            log.error("Anomaly analysis failed: {}", e.getMessage());
        }
    }

    private void runSummaryAnalysis(Statement statement, List<Transaction> transactions) {
        long start = Instant.now().toEpochMilli();

        Map<String, Double> categoryTotals = new HashMap<>();
        transactions.forEach(t -> {
            if (t.getCategory() != null) {
                categoryTotals.merge(t.getCategory(), t.getAmount().doubleValue(), Double::sum);
            }
        });

        double totalSpend = transactions.stream()
            .filter(t -> t.getType() == Transaction.TransactionType.DEBIT)
            .mapToDouble(t -> t.getAmount().doubleValue())
            .sum();

        String prompt = String.format("""
            Write a brief financial summary for this statement period.

            Total spending: $%.2f
            Category breakdown: %s
            Transaction count: %d

            Write 2-3 sentences of actionable financial insight. Be specific with numbers.
            Return JSON: {"summary": "...", "topInsight": "...", "recommendation": "..."}
            """,
            totalSpend, toJson(categoryTotals), transactions.size());

        try {
            Map<String, Object> result = ollamaService.chatJson(prompt);
            long processingMs = Instant.now().toEpochMilli() - start;
            saveAnalysisResult(statement, AnalysisResult.AnalysisType.SUMMARY, result, processingMs);
        } catch (Exception e) {
            log.error("Summary analysis failed: {}", e.getMessage());
        }
    }

    private void saveAnalysisResult(Statement statement, AnalysisResult.AnalysisType type,
                                     Map<String, Object> result, long processingMs) {
        AnalysisResult ar = AnalysisResult.builder()
            .statement(statement)
            .analysisType(type)
            .resultData(result)
            .modelUsed(model)
            .processingMs((int) processingMs)
            .build();
        analysisResultRepository.save(ar);
    }

    public List<AnalysisResponse> getAnalysisResults(UUID statementId) {
        return analysisResultRepository.findByStatementIdOrderByCreatedAtDesc(statementId)
            .stream()
            .map(AnalysisResponse::from)
            .collect(Collectors.toList());
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}

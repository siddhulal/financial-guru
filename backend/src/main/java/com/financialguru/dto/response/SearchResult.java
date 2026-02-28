package com.financialguru.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private String query;
    private List<TransactionResponse> transactions;
    private List<AccountResponse> accounts;
    private List<String> merchants;
    private int totalResults;
}

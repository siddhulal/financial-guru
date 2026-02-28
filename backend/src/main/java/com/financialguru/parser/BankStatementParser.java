package com.financialguru.parser;

import com.financialguru.model.Account;
import com.financialguru.model.Statement;
import com.financialguru.model.Transaction;

import java.util.List;

public interface BankStatementParser {
    boolean supports(String institution);
    List<Transaction> parse(String pdfText, Statement statement, Account account);

    /**
     * Extract account-level metadata from the statement text (APR, promo APR, etc.).
     * Implementations update the account object in place. Default is a no-op.
     */
    default void extractAccountInfo(String pdfText, Account account) {}
}

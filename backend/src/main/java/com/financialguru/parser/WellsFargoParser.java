package com.financialguru.parser;

import com.financialguru.model.Account;
import com.financialguru.model.Statement;
import com.financialguru.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class WellsFargoParser extends GenericPdfParser {

    @Override
    public boolean supports(String institution) {
        return "WELLS_FARGO".equals(institution);
    }

    @Override
    public List<Transaction> parse(String pdfText, Statement statement, Account account) {
        log.info("Using Wells Fargo parser");
        List<Transaction> transactions = super.parse(pdfText, statement, account);
        log.info("Wells Fargo parser extracted {} transactions", transactions.size());
        return transactions;
    }
}

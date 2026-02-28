package com.financialguru.service;

import com.financialguru.model.Transaction;
import com.financialguru.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final TransactionRepository transactionRepository;

    public byte[] exportTransactionsCSV(LocalDate from, LocalDate to, String accountId, String category) {
        List<Transaction> transactions;
        if (accountId != null) {
            transactions = transactionRepository.findByAccountId(UUID.fromString(accountId));
            transactions = transactions.stream()
                    .filter(t -> !t.getTransactionDate().isBefore(from)
                            && !t.getTransactionDate().isAfter(to))
                    .collect(Collectors.toList());
        } else {
            transactions = transactionRepository.findAll().stream()
                    .filter(t -> !t.getTransactionDate().isBefore(from)
                            && !t.getTransactionDate().isAfter(to))
                    .collect(Collectors.toList());
        }

        if (category != null && !category.isBlank()) {
            transactions = transactions.stream()
                    .filter(t -> category.equalsIgnoreCase(t.getCategory()))
                    .collect(Collectors.toList());
        }

        transactions.sort(Comparator.comparing(Transaction::getTransactionDate).reversed());

        StringBuilder sb = new StringBuilder();
        sb.append("Date,Merchant,Category,Amount,Type,Account,Description\n");
        for (Transaction t : transactions) {
            sb.append(escape(t.getTransactionDate().toString())).append(",")
                    .append(escape(t.getMerchantName())).append(",")
                    .append(escape(t.getCategory())).append(",")
                    .append(t.getAmount()).append(",")
                    .append(t.getType()).append(",")
                    .append(escape(t.getAccount() != null ? t.getAccount().getName() : "")).append(",")
                    .append(escape(t.getDescription())).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    public byte[] exportMonthlySummaryPDF(int year, int month) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            String monthName = start.getMonth().getDisplayName(TextStyle.FULL, Locale.US);

            BigDecimal totalSpend = transactionRepository.sumAllSpending(start, end);
            if (totalSpend == null) totalSpend = BigDecimal.ZERO;
            List<Object[]> cats = transactionRepository.findAllCategoryTotals(start, end);

            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(boldFont, 20);
                cs.newLineAtOffset(50, 780);
                cs.showText("Monthly Financial Summary — " + monthName + " " + year);
                cs.setFont(regularFont, 12);
                cs.newLineAtOffset(0, -30);
                cs.showText("Total Spending: $" + totalSpend.setScale(2, RoundingMode.HALF_UP));
                cs.newLineAtOffset(0, -20);
                cs.showText("Generated: " + LocalDate.now());
                cs.newLineAtOffset(0, -30);
                cs.setFont(boldFont, 14);
                cs.showText("Spending by Category:");
                cs.setFont(regularFont, 12);
                float y = -25;
                for (Object[] row : cats) {
                    cs.newLineAtOffset(0, y);
                    cs.showText("  " + row[0] + ": $"
                            + ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP));
                    y = -18;
                }
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}

package com.financialguru.service;

import com.financialguru.model.Account;
import com.financialguru.model.Statement;
import com.financialguru.model.Transaction;
import com.financialguru.parser.BankStatementParser;
import com.financialguru.parser.GenericPdfParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfExtractionService {

    // Minimum meaningful character count — anything below this means the PDF has no text layer
    private static final int MIN_TEXT_CHARS = 50;

    // Tesseract binary — checked in order
    private static final String[] TESSERACT_PATHS = {
        "/opt/homebrew/bin/tesseract",  // Apple Silicon Mac
        "/usr/local/bin/tesseract",     // Intel Mac
        "tesseract"                     // fallback: PATH
    };

    private final List<BankStatementParser> parsers;
    private final GenericPdfParser genericPdfParser;

    public List<Transaction> extractTransactions(File pdfFile, Statement statement, Account account) {
        String text = extractText(pdfFile);
        String institution = detectInstitution(text);
        log.info("Detected institution: {} for file: {}", institution, pdfFile.getName());

        BankStatementParser parser = parsers.stream()
            .filter(p -> p.supports(institution))
            .findFirst()
            .orElse(genericPdfParser);

        List<Transaction> transactions = parser.parse(text, statement, account);

        // Extract account-level metadata (APR, promo APR, etc.) if we have an account
        if (account != null) {
            parser.extractAccountInfo(text, account);
        }

        return transactions;
    }

    public String extractText(File pdfFile) {
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            // ── 1. Try native text extraction (sorted by position for multi-column layout) ──
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);

            // ── 1b. Append AcroForm field values (some PDFs store values in form fields) ─
            text = appendAcroFormFields(doc, text);

            if (text != null && text.trim().length() >= MIN_TEXT_CHARS) {
                log.info("Extracted {} chars via PDFBox text layer", text.trim().length());
                return text;
            }

            // ── 2. Image-only PDF — fall back to Tesseract OCR ──────────────
            log.info("PDF has no text layer ({} chars). Attempting OCR with Tesseract...",
                text == null ? 0 : text.trim().length());
            return extractTextWithOcr(doc);

        } catch (IOException e) {
            log.error("Failed to extract text from PDF: {}", e.getMessage());
            throw new RuntimeException("PDF text extraction failed", e);
        }
    }

    private String extractTextWithOcr(PDDocument doc) {
        String tesseract = findTesseract();
        if (tesseract == null) {
            throw new RuntimeException(
                "This PDF has no text layer and Tesseract OCR is not installed. " +
                "Install it with: brew install tesseract");
        }

        StringBuilder result = new StringBuilder();
        PDFRenderer renderer = new PDFRenderer(doc);
        int pages = doc.getNumberOfPages();
        log.info("Running OCR on {} page(s) using {}", pages, tesseract);

        for (int pageNum = 0; pageNum < pages; pageNum++) {
            File tempPng = null;
            try {
                // Render at 300 DPI — good balance of quality vs speed
                BufferedImage image = renderer.renderImageWithDPI(pageNum, 300);

                tempPng = Files.createTempFile("ocr_pg" + pageNum + "_", ".png").toFile();
                ImageIO.write(image, "PNG", tempPng);

                // Run: tesseract <image> stdout --psm 6
                // psm 6 = assume a single uniform block of text (good for statements)
                ProcessBuilder pb = new ProcessBuilder(
                    tesseract, tempPng.getAbsolutePath(), "stdout",
                    "--psm", "6",
                    "-l", "eng"
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String pageText = new String(process.getInputStream().readAllBytes());
                int exit = process.waitFor();

                if (exit == 0 && !pageText.isBlank()) {
                    log.info("OCR page {}/{}: {} chars", pageNum + 1, pages, pageText.length());
                    result.append(pageText).append("\n");
                } else {
                    log.warn("OCR page {}/{} returned exit code {} or empty text", pageNum + 1, pages, exit);
                }

            } catch (Exception e) {
                log.error("OCR failed on page {}: {}", pageNum + 1, e.getMessage());
            } finally {
                if (tempPng != null) tempPng.delete();
            }
        }

        String text = result.toString();
        log.info("OCR complete: {} total chars across {} pages", text.length(), pages);
        return text;
    }

    public String detectInstitution(String text) {
        // Use only the header (first 1500 chars) to avoid false positives.
        // e.g. an Amex statement mentions "Bank of America, NA" as the AutoPay bank,
        // but the actual issuer "American Express" is always in the first few lines.
        String header = text.substring(0, Math.min(1500, text.length())).toLowerCase();

        // Most-specific / most-likely-to-appear-in-other-statements first
        if (header.contains("american express") || header.contains("americanexpress.com")) return "AMEX";
        if (header.contains("bank of america") || header.contains("bankofamerica")) return "BANK_OF_AMERICA";
        if (header.contains("wells fargo")) return "WELLS_FARGO";
        if (header.contains("citibank") || header.contains("citi card") || header.contains("citicards")) return "CITI";
        if (header.contains("capital one") || header.contains("capitalone.com")) return "CAPITAL_ONE";
        if (header.contains("chase") || header.contains("jpmorgan")) return "CHASE";
        if (header.contains("discover") || header.contains("dfs services")) return "DISCOVER";
        if (header.contains("goldman sachs") || header.contains("apple card") || header.contains("applecard.apple.com")) return "GOLDMAN_SACHS";

        // Fallback: scan full text if header had no match
        String lower = text.toLowerCase();
        if (lower.contains("american express") || lower.contains("americanexpress.com")) return "AMEX";
        if (lower.contains("bank of america") || lower.contains("bankofamerica")) return "BANK_OF_AMERICA";
        if (lower.contains("wells fargo")) return "WELLS_FARGO";
        if (lower.contains("citibank") || lower.contains("citi card")) return "CITI";
        if (lower.contains("capital one") || lower.contains("capitalone.com")) return "CAPITAL_ONE";
        if (lower.contains("chase") || lower.contains("jpmorgan")) return "CHASE";
        if (lower.contains("discover") || lower.contains("dfs services")) return "DISCOVER";
        if (lower.contains("goldman sachs") || lower.contains("apple card") || lower.contains("applecard.apple.com")) return "GOLDMAN_SACHS";
        return "GENERIC";
    }

    /**
     * Appends AcroForm (interactive PDF form) field values to the extracted text.
     * Chase PDF statements store some key values (Minimum Payment Due, Payment Due Date)
     * as form fields that don't appear in the normal text extraction stream.
     */
    private String appendAcroFormFields(PDDocument doc, String text) {
        try {
            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            if (acroForm == null) return text;

            StringBuilder extra = new StringBuilder();
            for (PDField field : acroForm.getFieldTree()) {
                try {
                    String name  = field.getFullyQualifiedName();
                    String value = field.getValueAsString();
                    if (name != null && value != null && !value.isBlank() && !value.equals("Off")) {
                        extra.append(name).append(" ").append(value).append("\n");
                        log.debug("AcroForm field: {} = {}", name, value);
                    }
                } catch (Exception ignored) {}
            }

            if (!extra.isEmpty()) {
                log.info("Appending {} AcroForm field values to extracted text", extra.toString().split("\n").length);
                return text + "\n" + extra;
            }
        } catch (Exception e) {
            log.debug("Could not read AcroForm fields: {}", e.getMessage());
        }
        return text;
    }

    private String findTesseract() {
        for (String path : TESSERACT_PATHS) {
            File f = new File(path);
            if (path.equals("tesseract") || f.exists()) {
                try {
                    new ProcessBuilder(path, "--version")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor();
                    return path;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}

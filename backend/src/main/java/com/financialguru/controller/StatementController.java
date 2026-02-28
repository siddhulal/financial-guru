package com.financialguru.controller;

import com.financialguru.model.Statement;
import com.financialguru.service.PdfExtractionService;
import com.financialguru.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
@Tag(name = "Statements", description = "Upload and manage bank statements")
public class StatementController {

    private final StatementService statementService;
    private final PdfExtractionService pdfExtractionService;

    @PostMapping("/upload")
    @Operation(summary = "Upload a PDF bank statement")
    public ResponseEntity<Statement> uploadStatement(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "accountId", required = false) UUID accountId
    ) throws Exception {
        return ResponseEntity.ok(statementService.uploadStatement(file, accountId));
    }

    @GetMapping
    @Operation(summary = "List all statements")
    public ResponseEntity<List<Statement>> getAllStatements() {
        return ResponseEntity.ok(statementService.getAllStatements());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get statement detail and processing status")
    public ResponseEntity<Statement> getStatement(@PathVariable UUID id) {
        return ResponseEntity.ok(statementService.getStatement(id));
    }

    @PostMapping("/{id}/reprocess")
    @Operation(summary = "Re-run PDF parsing for a statement")
    public ResponseEntity<Void> reprocessStatement(@PathVariable UUID id) {
        statementService.reprocessStatement(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a statement and its transactions")
    public ResponseEntity<Void> deleteStatement(@PathVariable UUID id) {
        statementService.deleteStatement(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/assign-account/{accountId}")
    @Operation(summary = "Link a statement (and its transactions) to an account")
    public ResponseEntity<Statement> assignAccount(
        @PathVariable UUID id,
        @PathVariable UUID accountId
    ) {
        return ResponseEntity.ok(statementService.assignAccount(id, accountId));
    }

    @GetMapping("/{id}/raw-text")
    @Operation(summary = "Get raw PDF text extracted by PDFBox — useful for diagnosing parse issues")
    public ResponseEntity<Map<String, Object>> getRawText(@PathVariable UUID id) {
        Statement statement = statementService.getStatement(id);
        File pdfFile = new File(statement.getFilePath());
        if (!pdfFile.exists()) {
            return ResponseEntity.notFound().build();
        }
        String text = pdfExtractionService.extractText(pdfFile);
        String institution = pdfExtractionService.detectInstitution(text);
        return ResponseEntity.ok(Map.of(
            "fileName", statement.getFileName(),
            "institution", institution,
            "charCount", text.length(),
            "lineCount", text.split("\n").length,
            "text", text
        ));
    }
}

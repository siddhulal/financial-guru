package com.financialguru.controller;

import com.financialguru.dto.request.ChatMessageRequest;
import com.financialguru.service.FinancialAdvisorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "AI financial advisor chat")
public class ChatController {

    private final FinancialAdvisorService advisorService;

    @PostMapping
    @Operation(summary = "Chat with the AI financial advisor")
    public ResponseEntity<Map<String, String>> chat(
        @Valid @RequestBody ChatMessageRequest request
    ) {
        String response = advisorService.chat(request.getMessage());
        return ResponseEntity.ok(Map.of(
            "message", request.getMessage(),
            "response", response
        ));
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Get suggested questions for the financial advisor")
    public ResponseEntity<List<String>> getSuggestions() {
        return ResponseEntity.ok(advisorService.getSuggestedQuestions());
    }
}

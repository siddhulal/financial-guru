package com.financialguru.controller;

import com.financialguru.dto.request.ChatMessageRequest;
import com.financialguru.dto.response.SavingsPlanResponse;
import com.financialguru.service.FinancialAdvisorService;
import com.financialguru.service.SavingsPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "AI financial advisor chat")
public class ChatController {

    private final FinancialAdvisorService advisorService;
    private final SavingsPlanService savingsPlanService;

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

    @PostMapping("/savings-plan")
    @Operation(summary = "Generate a personalized savings plan to hit a monthly savings target")
    public ResponseEntity<SavingsPlanResponse> getSavingsPlan(
            @RequestParam(defaultValue = "300") BigDecimal target) {
        return ResponseEntity.ok(savingsPlanService.buildPlan(target));
    }

    @PostMapping("/enriched")
    @Operation(summary = "Chat with full financial context injected — income, spending, savings rate")
    public ResponseEntity<Map<String, String>> chatEnriched(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String response = advisorService.chatWithFullContext(message);
        return ResponseEntity.ok(Map.of("response", response));
    }
}

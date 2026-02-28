package com.financialguru.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class OllamaService {

    private final RestTemplate ollamaRestTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    @Value("${app.ollama.model:gemma3:4b}")
    private String model;

    public OllamaService(@Qualifier("ollamaRestTemplate") RestTemplate ollamaRestTemplate,
                         ObjectMapper objectMapper,
                         @Qualifier("ollamaBaseUrl") String baseUrl) {
        this.ollamaRestTemplate = ollamaRestTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    /**
     * Send a prompt to Ollama and get a text response.
     */
    public String chat(String prompt) {
        return chat(prompt, false);
    }

    public String chat(String systemContext, String userMessage) {
        String fullPrompt = systemContext + "\n\n" + userMessage;
        return chat(fullPrompt, false);
    }

    public String chat(String prompt, boolean stream) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("prompt", prompt);
            request.put("stream", stream);
            request.put("options", Map.of(
                "temperature", 0.3,
                "top_p", 0.9,
                "num_predict", 2048
            ));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = ollamaRestTemplate.postForObject(
                baseUrl + "/api/generate", request, Map.class);

            if (response != null && response.containsKey("response")) {
                return (String) response.get("response");
            }
            return "";
        } catch (Exception e) {
            log.error("Ollama API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to communicate with Ollama: " + e.getMessage(), e);
        }
    }

    /**
     * Parse Ollama response as JSON map.
     */
    public Map<String, Object> chatJson(String prompt) {
        String jsonPrompt = prompt + "\n\nIMPORTANT: Respond ONLY with valid JSON. No markdown, no explanation, just JSON.";
        String response = chat(jsonPrompt);
        try {
            // Extract JSON from response (handle cases where model adds extra text)
            String cleaned = extractJson(response);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(cleaned, Map.class);
            return result;
        } catch (Exception e) {
            log.error("Failed to parse Ollama JSON response: {}", response);
            return Map.of("raw_response", response, "parse_error", e.getMessage());
        }
    }

    private String extractJson(String text) {
        // Find first { and last } to extract JSON object
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        // Try array
        start = text.indexOf('[');
        end = text.lastIndexOf(']');
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    public boolean isAvailable() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = ollamaRestTemplate.getForObject(baseUrl + "/api/tags", Map.class);
            return response != null;
        } catch (Exception e) {
            log.warn("Ollama is not available: {}", e.getMessage());
            return false;
        }
    }
}

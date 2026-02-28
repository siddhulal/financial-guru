package com.financialguru.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatMessageRequest {

    @NotBlank(message = "Message cannot be empty")
    private String message;

    private String conversationId;
}

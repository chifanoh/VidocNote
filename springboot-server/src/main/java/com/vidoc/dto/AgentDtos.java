package com.vidoc.dto;

import jakarta.validation.constraints.NotBlank;

public final class AgentDtos {

    private AgentDtos() {
    }

    public record ChatRequest(
            @NotBlank(message = "问题不能为空")
            String question) {
    }

    public record ChatResponse(String answer) {
    }
}

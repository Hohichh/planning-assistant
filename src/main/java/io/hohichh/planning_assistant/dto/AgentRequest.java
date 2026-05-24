package io.hohichh.planning_assistant.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record AgentRequest(
        @NotBlank String message,
        UUID taskId
) {
}

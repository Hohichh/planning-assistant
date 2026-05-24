package io.hohichh.planning_assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record TaskRequest(
        @NotBlank String title,
        String description,
        @NotNull Instant deadline,
        @NotBlank String status
) {
}

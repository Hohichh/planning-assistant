package io.hohichh.planning_assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SubtaskUpdateRequest(
        @NotBlank String title,
        @NotNull @Positive Integer estimatedMinutes,
        String cognitiveLoad,
        Integer sortOrder
) {
}

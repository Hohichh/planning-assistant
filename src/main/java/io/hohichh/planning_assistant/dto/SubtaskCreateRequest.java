package io.hohichh.planning_assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record SubtaskCreateRequest(
        @NotNull UUID taskId,
        @NotBlank String title,
        @NotNull @Positive Integer estimatedMinutes,
        String cognitiveLoad,
        Integer sortOrder,
        Instant startTime,
        Instant endTime
) {
}

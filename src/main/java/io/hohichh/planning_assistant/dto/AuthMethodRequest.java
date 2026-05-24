package io.hohichh.planning_assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AuthMethodRequest(
        @NotNull UUID userId,
        @NotBlank String provider,
        @NotBlank String externalId,
        String passwordHash
) {
}

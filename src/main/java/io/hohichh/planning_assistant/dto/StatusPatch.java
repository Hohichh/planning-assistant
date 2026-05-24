package io.hohichh.planning_assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusPatch(
        @NotBlank String status
) {
}

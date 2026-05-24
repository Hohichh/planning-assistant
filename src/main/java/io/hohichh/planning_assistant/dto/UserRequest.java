package io.hohichh.planning_assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record UserRequest(
        @NotBlank String name
) {
}

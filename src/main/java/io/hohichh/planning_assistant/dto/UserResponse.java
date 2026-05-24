package io.hohichh.planning_assistant.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        Instant createdAt
) {
}

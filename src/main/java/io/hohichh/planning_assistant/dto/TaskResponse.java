package io.hohichh.planning_assistant.dto;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID userId,
        String title,
        String description,
        Instant deadline,
        String status,
        Instant createdAt
) {
}

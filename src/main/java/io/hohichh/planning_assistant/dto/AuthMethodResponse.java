package io.hohichh.planning_assistant.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthMethodResponse(
        UUID id,
        UUID userId,
        String provider,
        String externalId,
        Instant createdAt
) {
}

package io.hohichh.planning_assistant.dto;

import java.time.Instant;
import java.util.UUID;

public record TimeslotResponse(
        UUID id,
        UUID subtaskId,
        UUID userId,
        Instant startTime,
        Instant endTime,
        Boolean isCommitted
) {
}

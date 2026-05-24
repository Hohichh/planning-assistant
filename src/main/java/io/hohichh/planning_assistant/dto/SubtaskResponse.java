package io.hohichh.planning_assistant.dto;

import java.time.Instant;
import java.util.UUID;

public record SubtaskResponse(
        UUID id,
        UUID taskId,
        UUID userId,
        String title,
        Integer estimatedMinutes,
        String cognitiveLoad,
        Integer sortOrder,
        Boolean isCompleted,
        String status,
        UUID replacesId,
        Instant createdAt,
        TimeslotResponse timeslot
) {
}

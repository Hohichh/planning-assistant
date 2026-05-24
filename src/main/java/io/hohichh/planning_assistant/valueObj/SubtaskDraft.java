package io.hohichh.planning_assistant.valueObj;

import io.hohichh.planning_assistant.enums.CognitiveLoad;

import java.time.Instant;
import java.util.UUID;

public record SubtaskDraft(
        UUID taskId,
        String title,
        Integer estimatedMinutes,
        CognitiveLoad cognitiveLoad,
        Integer sortOrder,
        Instant startTime,
        Instant endTime
) {
}

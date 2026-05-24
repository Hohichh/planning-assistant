package io.hohichh.planning_assistant.valueObj;

import java.time.Instant;
import java.util.UUID;

public record ProposedTimeslot(
        UUID subtaskId,
        Instant startTime,
        Instant endTime
) {
}

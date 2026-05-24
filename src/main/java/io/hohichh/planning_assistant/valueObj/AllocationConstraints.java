package io.hohichh.planning_assistant.valueObj;

import java.time.Instant;

public record AllocationConstraints(
        Instant rangeStart,
        Instant rangeEnd,
        Integer maxSlotMinutes,
        Integer minBreakMinutes,
        Instant deadline,
        String preferredTimeOfDay,
        boolean avoidWeekends,
        Integer maxDailyMinutes
) {

    public static AllocationConstraints withDefaults(Instant rangeStart, Instant rangeEnd) {
        return new AllocationConstraints(
                rangeStart, rangeEnd,
                90, 10,
                null, "ANY", false, null
        );
    }
}


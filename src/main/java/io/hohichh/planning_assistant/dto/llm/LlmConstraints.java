package io.hohichh.planning_assistant.dto.llm;

public record LlmConstraints(
        String deadlineSemantic,
        String deadlineRawText,
        String priority,
        String preferredTimeOfDay,
        Boolean avoidWeekends,
        Integer maxDailyMinutes
) {
}

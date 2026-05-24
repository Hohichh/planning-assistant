package io.hohichh.planning_assistant.dto.llm;

import java.util.List;

public record LlmPlannerResponse(
        String taskTitle,
        List<LlmSubtaskItem> subtasks,
        LlmConstraints constraints
) {
}

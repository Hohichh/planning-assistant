package io.hohichh.planning_assistant.dto.llm;

public record LlmSubtaskItem(
        String title,
        Integer estimatedMinutes,
        String cognitiveLoad
) {
}

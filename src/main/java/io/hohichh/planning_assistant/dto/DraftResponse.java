package io.hohichh.planning_assistant.dto;

import java.util.List;

public record DraftResponse(
        List<SubtaskResponse> subtasks,
        Boolean hasModifications
) {
}

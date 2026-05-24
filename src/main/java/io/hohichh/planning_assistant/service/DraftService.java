package io.hohichh.planning_assistant.service;

import io.hohichh.planning_assistant.dto.DraftResponse;
import io.hohichh.planning_assistant.dto.SubtaskResponse;
import io.hohichh.planning_assistant.model.Timeslot;
import io.hohichh.planning_assistant.valueObj.SubtaskDraft;

import java.util.List;
import java.util.UUID;

public interface DraftService {

    DraftResponse createDraftBatch(UUID userId, List<SubtaskDraft> drafts);

    SubtaskResponse proposeDraftModification(UUID userId, UUID subtaskId, Timeslot newTimeslot);

    List<SubtaskResponse> commitDraft(UUID userId);

    void rejectDraft(UUID userId);

    DraftResponse getDraft(UUID userId);
}

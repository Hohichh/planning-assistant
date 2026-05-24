package io.hohichh.planning_assistant.service.impl;

import io.hohichh.planning_assistant.dto.DraftResponse;
import io.hohichh.planning_assistant.dto.SubtaskResponse;
import io.hohichh.planning_assistant.enums.SubtaskStatus;
import io.hohichh.planning_assistant.mapping.SubtaskMapper;
import io.hohichh.planning_assistant.model.Subtask;
import io.hohichh.planning_assistant.model.Task;
import io.hohichh.planning_assistant.model.Timeslot;
import io.hohichh.planning_assistant.repository.SubtaskRepository;
import io.hohichh.planning_assistant.repository.TaskRepository;
import io.hohichh.planning_assistant.repository.TimeslotRepository;
import io.hohichh.planning_assistant.service.DraftService;
import io.hohichh.planning_assistant.valueObj.SubtaskDraft;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DraftServiceImpl implements DraftService {

    private static final List<SubtaskStatus> DRAFT_STATUSES =
            List.of(SubtaskStatus.DRAFT, SubtaskStatus.PENDING_DELETE);

    private final SubtaskRepository subtaskRepository;
    private final TimeslotRepository timeslotRepository;
    private final TaskRepository taskRepository;
    private final SubtaskMapper subtaskMapper;

    @Override
    @Transactional
    public DraftResponse createDraftBatch(UUID userId, List<SubtaskDraft> drafts) {
        List<Subtask> subtasks = new ArrayList<>();
        List<Timeslot> timeslots = new ArrayList<>();

        for (SubtaskDraft draft : drafts) {
            Task task = taskRepository.findById(draft.taskId())
                    .orElseThrow(() -> new EntityNotFoundException("Task not found: " + draft.taskId()));

            Subtask subtask = Subtask.builder()
                    .task(task)
                    .user(task.getUser())
                    .title(draft.title())
                    .estimatedMinutes(draft.estimatedMinutes())
                    .cognitiveLoad(draft.cognitiveLoad())
                    .sortOrder(draft.sortOrder())
                    .isCompleted(false)
                    .status(SubtaskStatus.DRAFT)
                    .build();
            subtasks.add(subtask);

            if (draft.startTime() != null && draft.endTime() != null) {
                Timeslot timeslot = Timeslot.builder()
                        .subtask(subtask)
                        .user(task.getUser())
                        .startTime(draft.startTime())
                        .endTime(draft.endTime())
                        .isCommitted(false)
                        .build();
                timeslots.add(timeslot);
            }
        }

        List<Subtask> savedSubtasks = subtaskRepository.saveAll(subtasks);
        timeslotRepository.saveAll(timeslots);

        List<SubtaskResponse> responses = savedSubtasks.stream()
                .map(subtaskMapper::toResponse)
                .toList();

        return new DraftResponse(responses, false);
    }

    @Override
    @Transactional
    public SubtaskResponse proposeDraftModification(UUID userId, UUID subtaskId, Timeslot newTimeslot) {
        Subtask original = subtaskRepository.findById(subtaskId)
                .orElseThrow(() -> new EntityNotFoundException("Subtask not found: " + subtaskId));

        original.setStatus(SubtaskStatus.PENDING_DELETE);
        subtaskRepository.save(original);

        Subtask draft = Subtask.builder()
                .task(original.getTask())
                .user(original.getUser())
                .title(original.getTitle())
                .estimatedMinutes(original.getEstimatedMinutes())
                .cognitiveLoad(original.getCognitiveLoad())
                .sortOrder(original.getSortOrder())
                .isCompleted(false)
                .status(SubtaskStatus.DRAFT)
                .replaces(original)
                .build();

        Subtask savedDraft = subtaskRepository.save(draft);

        newTimeslot.setSubtask(savedDraft);
        newTimeslot.setUser(original.getUser());
        newTimeslot.setIsCommitted(false);
        timeslotRepository.save(newTimeslot);

        return subtaskMapper.toResponse(savedDraft);
    }

    @Override
    @Transactional
    public List<SubtaskResponse> commitDraft(UUID userId) {
        List<Subtask> draftSubtasks = subtaskRepository.findByUserIdAndStatusIn(userId, DRAFT_STATUSES);

        List<Subtask> toSave = new ArrayList<>();
        List<Timeslot> timeslotsToCommit = new ArrayList<>();

        for (Subtask subtask : draftSubtasks) {
            if (subtask.getStatus() == SubtaskStatus.DRAFT) {
                subtask.setStatus(SubtaskStatus.SCHEDULED);
                toSave.add(subtask);
                for (Timeslot ts : subtask.getTimeslots()) {
                    ts.setIsCommitted(true);
                    timeslotsToCommit.add(ts);
                }
            } else if (subtask.getStatus() == SubtaskStatus.PENDING_DELETE) {
                subtaskRepository.delete(subtask);
            }
        }

        subtaskRepository.saveAll(toSave);
        timeslotRepository.saveAll(timeslotsToCommit);

        return toSave.stream()
                .map(subtaskMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void rejectDraft(UUID userId) {
        subtaskRepository.deleteAllByUserIdAndStatus(userId, SubtaskStatus.DRAFT);

        List<Subtask> pendingDelete = subtaskRepository.findByUserIdAndStatus(userId, SubtaskStatus.PENDING_DELETE);
        for (Subtask subtask : pendingDelete) {
            subtask.setStatus(SubtaskStatus.SCHEDULED);
        }
        subtaskRepository.saveAll(pendingDelete);

        timeslotRepository.deleteByUserIdAndIsCommitted(userId, false);
    }

    @Override
    public DraftResponse getDraft(UUID userId) {
        List<Subtask> draftSubtasks = subtaskRepository.findByUserIdAndStatusIn(userId, DRAFT_STATUSES);
        boolean hasModifications = draftSubtasks.stream()
                .anyMatch(s -> s.getStatus() == SubtaskStatus.PENDING_DELETE);

        List<SubtaskResponse> responses = draftSubtasks.stream()
                .map(subtaskMapper::toResponse)
                .toList();

        return new DraftResponse(responses, hasModifications);
    }
}

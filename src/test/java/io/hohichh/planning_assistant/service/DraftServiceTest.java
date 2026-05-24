package io.hohichh.planning_assistant.service;

import io.hohichh.planning_assistant.dto.DraftResponse;
import io.hohichh.planning_assistant.dto.SubtaskResponse;
import io.hohichh.planning_assistant.enums.CognitiveLoad;
import io.hohichh.planning_assistant.enums.SubtaskStatus;
import io.hohichh.planning_assistant.mapping.SubtaskMapper;
import io.hohichh.planning_assistant.model.Subtask;
import io.hohichh.planning_assistant.model.Task;
import io.hohichh.planning_assistant.model.Timeslot;
import io.hohichh.planning_assistant.model.User;
import io.hohichh.planning_assistant.repository.SubtaskRepository;
import io.hohichh.planning_assistant.repository.TaskRepository;
import io.hohichh.planning_assistant.repository.TimeslotRepository;
import io.hohichh.planning_assistant.service.impl.DraftServiceImpl;
import io.hohichh.planning_assistant.valueObj.SubtaskDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftServiceTest {

    @Mock
    private SubtaskRepository subtaskRepository;
    @Mock
    private TimeslotRepository timeslotRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private SubtaskMapper subtaskMapper;

    @InjectMocks
    private DraftServiceImpl draftService;

    private UUID userId;
    private UUID taskId;
    private User user;
    private Task task;
    private Instant now;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        now = Instant.now();
        user = User.builder().id(userId).name("Alice").build();
        task = Task.builder().id(taskId).user(user).title("Math").build();
    }

    @Test
    void createDraftBatch_shouldSaveSubtasksAndTimeslots() {
        Instant start = now.plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(60, ChronoUnit.MINUTES);

        SubtaskDraft draft = new SubtaskDraft(taskId, "Chapter 1", 60, CognitiveLoad.MEDIUM, 1, start, end);

        Subtask savedSubtask = Subtask.builder()
                .id(UUID.randomUUID())
                .task(task)
                .user(user)
                .title("Chapter 1")
                .status(SubtaskStatus.DRAFT)
                .build();

        SubtaskResponse resp = new SubtaskResponse(savedSubtask.getId(), taskId, userId,
                "Chapter 1", 60, "MEDIUM", 1, false, "DRAFT", null, now, null);

        when(taskRepository.findById(taskId)).thenReturn(java.util.Optional.of(task));
        when(subtaskRepository.saveAll(anyList())).thenReturn(List.of(savedSubtask));
        when(timeslotRepository.saveAll(anyList())).thenReturn(List.of());
        when(subtaskMapper.toResponse(any(Subtask.class))).thenReturn(resp);

        DraftResponse result = draftService.createDraftBatch(userId, List.of(draft));

        assertNotNull(result);
        assertEquals(1, result.subtasks().size());
        assertFalse(result.hasModifications());
    }

    @Test
    void commitDraft_shouldTransitionStatuses() {
        Subtask draftSubtask = Subtask.builder()
                .id(UUID.randomUUID())
                .task(task)
                .user(user)
                .title("Chapter 1")
                .status(SubtaskStatus.DRAFT)
                .timeslots(new ArrayList<>(List.of(
                        Timeslot.builder().id(UUID.randomUUID()).user(user).isCommitted(false).build()
                )))
                .build();

        SubtaskResponse resp = new SubtaskResponse(draftSubtask.getId(), taskId, userId,
                "Chapter 1", 60, "MEDIUM", 1, false, "SCHEDULED", null, now, null);

        when(subtaskRepository.findByUserIdAndStatusIn(eq(userId), any()))
                .thenReturn(List.of(draftSubtask));
        when(subtaskRepository.saveAll(anyList())).thenReturn(List.of(draftSubtask));
        when(timeslotRepository.saveAll(anyList())).thenReturn(List.of());
        when(subtaskMapper.toResponse(any(Subtask.class))).thenReturn(resp);

        List<SubtaskResponse> result = draftService.commitDraft(userId);

        assertEquals(1, result.size());
        assertEquals("SCHEDULED", result.getFirst().status());
    }

    @Test
    void rejectDraft_shouldDeleteDraftsAndRestorePendingDelete() {
        draftService.rejectDraft(userId);

        verify(subtaskRepository).deleteAllByUserIdAndStatus(userId, SubtaskStatus.DRAFT);
        verify(timeslotRepository).deleteByUserIdAndIsCommitted(userId, false);
    }

    @Test
    void getDraft_shouldReturnDraftSubtasks() {
        when(subtaskRepository.findByUserIdAndStatusIn(eq(userId), any()))
                .thenReturn(List.of());

        DraftResponse result = draftService.getDraft(userId);

        assertNotNull(result);
        assertEquals(0, result.subtasks().size());
    }
}

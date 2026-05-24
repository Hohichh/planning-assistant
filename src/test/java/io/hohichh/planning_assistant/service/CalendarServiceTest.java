package io.hohichh.planning_assistant.service;

import io.hohichh.planning_assistant.dto.SubtaskCreateRequest;
import io.hohichh.planning_assistant.dto.SubtaskResponse;
import io.hohichh.planning_assistant.dto.TaskRequest;
import io.hohichh.planning_assistant.dto.TaskResponse;
import io.hohichh.planning_assistant.dto.TimeslotResponse;
import io.hohichh.planning_assistant.enums.SubtaskStatus;
import io.hohichh.planning_assistant.enums.TaskStatus;
import io.hohichh.planning_assistant.mapping.SubtaskMapper;
import io.hohichh.planning_assistant.mapping.TaskMapper;
import io.hohichh.planning_assistant.mapping.TimeslotMapper;
import io.hohichh.planning_assistant.model.Subtask;
import io.hohichh.planning_assistant.model.Task;
import io.hohichh.planning_assistant.model.Timeslot;
import io.hohichh.planning_assistant.model.User;
import io.hohichh.planning_assistant.repository.SubtaskRepository;
import io.hohichh.planning_assistant.repository.TaskRepository;
import io.hohichh.planning_assistant.repository.TimeslotRepository;
import io.hohichh.planning_assistant.repository.UserRepository;
import io.hohichh.planning_assistant.service.impl.CalendarServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private SubtaskRepository subtaskRepository;
    @Mock
    private TimeslotRepository timeslotRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private SubtaskMapper subtaskMapper;
    @Mock
    private TimeslotMapper timeslotMapper;

    @InjectMocks
    private CalendarServiceImpl calendarService;

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
        task = Task.builder()
                .id(taskId)
                .user(user)
                .title("Study Math")
                .deadline(now.plus(30, ChronoUnit.DAYS))
                .status(TaskStatus.ACTIVE)
                .createdAt(now)
                .build();
    }

    @Test
    void createTask_shouldSaveAndReturn() {
        TaskRequest req = new TaskRequest("Study Math", null, now.plus(30, ChronoUnit.DAYS), "ACTIVE");
        TaskResponse resp = new TaskResponse(taskId, userId, "Study Math", null,
                now.plus(30, ChronoUnit.DAYS), "ACTIVE", now);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(taskMapper.toEntity(req)).thenReturn(task);
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(resp);

        TaskResponse result = calendarService.createTask(userId, req);

        assertNotNull(result);
        assertEquals("Study Math", result.title());
        verify(taskRepository).save(task);
    }

    @Test
    void getTask_shouldReturnResponse() {
        TaskResponse resp = new TaskResponse(taskId, userId, "Study Math", null,
                now.plus(30, ChronoUnit.DAYS), "ACTIVE", now);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskMapper.toResponse(task)).thenReturn(resp);

        TaskResponse result = calendarService.getTask(taskId);

        assertEquals(taskId, result.id());
    }

    @Test
    void getTask_shouldThrowWhenNotFound() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> calendarService.getTask(taskId));
    }

    @Test
    void deleteTask_shouldInvokeRepository() {
        when(taskRepository.existsById(taskId)).thenReturn(true);

        calendarService.deleteTask(taskId);

        verify(taskRepository).deleteById(taskId);
    }

    @Test
    void createSubtask_shouldSaveSubtaskAndTimeslot() {
        UUID subtaskId = UUID.randomUUID();
        Instant start = now.plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(60, ChronoUnit.MINUTES);

        SubtaskCreateRequest req = new SubtaskCreateRequest(taskId, "Chapter 1", 60, "MEDIUM", null, start, end);

        Subtask subtask = Subtask.builder()
                .id(subtaskId)
                .task(task)
                .user(user)
                .title("Chapter 1")
                .estimatedMinutes(60)
                .status(SubtaskStatus.SCHEDULED)
                .sortOrder(1)
                .isCompleted(false)
                .build();

        SubtaskResponse resp = new SubtaskResponse(subtaskId, taskId, userId, "Chapter 1", 60,
                "MEDIUM", 1, false, "SCHEDULED", null, now, null);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(subtaskRepository.save(any(Subtask.class))).thenReturn(subtask);
        when(timeslotRepository.save(any(Timeslot.class))).thenReturn(Timeslot.builder().build());
        when(subtaskMapper.toResponse(any(Subtask.class))).thenReturn(resp);

        SubtaskResponse result = calendarService.createSubtask(userId, req);

        assertNotNull(result);
        assertEquals("Chapter 1", result.title());
    }

    @Test
    void getOccupiedSlots_shouldReturnTimeslots() {
        Instant from = now;
        Instant to = now.plus(7, ChronoUnit.DAYS);
        Timeslot slot = Timeslot.builder()
                .id(UUID.randomUUID())
                .user(user)
                .startTime(from.plus(1, ChronoUnit.DAYS))
                .endTime(from.plus(1, ChronoUnit.DAYS).plus(60, ChronoUnit.MINUTES))
                .isCommitted(true)
                .build();
        TimeslotResponse slotResp = new TimeslotResponse(slot.getId(), null, userId,
                slot.getStartTime(), slot.getEndTime(), true);

        when(timeslotRepository.findByUserIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(userId, from, to))
                .thenReturn(List.of(slot));
        when(timeslotMapper.toResponse(slot)).thenReturn(slotResp);

        List<TimeslotResponse> result = calendarService.getOccupiedSlots(userId, from, to);

        assertEquals(1, result.size());
    }
}

package io.hohichh.planning_assistant.service.impl;

import io.hohichh.planning_assistant.dto.SubtaskCreateRequest;
import io.hohichh.planning_assistant.dto.SubtaskResponse;
import io.hohichh.planning_assistant.dto.SubtaskUpdateRequest;
import io.hohichh.planning_assistant.dto.TaskRequest;
import io.hohichh.planning_assistant.dto.TaskResponse;
import io.hohichh.planning_assistant.dto.TimeslotResponse;
import io.hohichh.planning_assistant.enums.CognitiveLoad;
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
import io.hohichh.planning_assistant.service.CalendarService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarServiceImpl implements CalendarService {

    private final TaskRepository taskRepository;
    private final SubtaskRepository subtaskRepository;
    private final TimeslotRepository timeslotRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;
    private final SubtaskMapper subtaskMapper;
    private final TimeslotMapper timeslotMapper;

    @Override
    @Transactional
    public TaskResponse createTask(UUID userId, TaskRequest request) {
        User user = findUserOrThrow(userId);
        Task task = taskMapper.toEntity(request);
        task.setUser(user);
        task.setStatus(TaskStatus.valueOf(request.status()));
        Task saved = taskRepository.save(task);
        return taskMapper.toResponse(saved);
    }

    @Override
    public List<TaskResponse> getTasks(UUID userId) {
        return taskRepository.findByUserId(userId).stream()
                .map(taskMapper::toResponse)
                .toList();
    }

    @Override
    public TaskResponse getTask(UUID id) {
        Task task = findTaskOrThrow(id);
        return taskMapper.toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse updateTask(UUID id, TaskRequest request) {
        Task task = findTaskOrThrow(id);
        taskMapper.updateEntity(request, task);
        task.setStatus(TaskStatus.valueOf(request.status()));
        Task saved = taskRepository.save(task);
        return taskMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteTask(UUID id) {
        if (!taskRepository.existsById(id)) {
            throw new EntityNotFoundException("Task not found: " + id);
        }
        taskRepository.deleteById(id);
    }

    @Override
    @Transactional
    public SubtaskResponse createSubtask(UUID userId, SubtaskCreateRequest request) {
        Task task = findTaskOrThrow(request.taskId());

        Integer sortOrder = request.sortOrder();
        if (sortOrder == null) {
            List<Subtask> existing = subtaskRepository.findByTaskIdOrderBySortOrder(request.taskId());
            sortOrder = existing.isEmpty() ? 1 : existing.getLast().getSortOrder() + 1;
        }

        Subtask subtask = Subtask.builder()
                .task(task)
                .user(task.getUser())
                .title(request.title())
                .estimatedMinutes(request.estimatedMinutes())
                .cognitiveLoad(parseCognitiveLoad(request.cognitiveLoad()))
                .sortOrder(sortOrder)
                .isCompleted(false)
                .status(SubtaskStatus.SCHEDULED)
                .build();

        Subtask savedSubtask = subtaskRepository.save(subtask);

        if (request.startTime() != null && request.endTime() != null) {
            Timeslot timeslot = Timeslot.builder()
                    .subtask(savedSubtask)
                    .user(task.getUser())
                    .startTime(request.startTime())
                    .endTime(request.endTime())
                    .isCommitted(true)
                    .build();
            timeslotRepository.save(timeslot);
        }

        return subtaskMapper.toResponse(savedSubtask);
    }

    @Override
    public SubtaskResponse getSubtask(UUID id) {
        Subtask subtask = findSubtaskOrThrow(id);
        return subtaskMapper.toResponse(subtask);
    }

    @Override
    public List<SubtaskResponse> getSubtasksByTask(UUID taskId) {
        return subtaskRepository.findByTaskIdOrderBySortOrder(taskId).stream()
                .map(subtaskMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SubtaskResponse updateSubtask(UUID id, SubtaskUpdateRequest request) {
        Subtask subtask = findSubtaskOrThrow(id);
        subtaskMapper.updateEntity(request, subtask);
        if (request.cognitiveLoad() != null) {
            subtask.setCognitiveLoad(CognitiveLoad.valueOf(request.cognitiveLoad()));
        }
        Subtask saved = subtaskRepository.save(subtask);
        return subtaskMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SubtaskResponse updateStatus(UUID id, SubtaskStatus status) {
        Subtask subtask = findSubtaskOrThrow(id);
        subtask.setStatus(status);
        if (status == SubtaskStatus.COMPLETED) {
            subtask.setIsCompleted(true);
        }
        Subtask saved = subtaskRepository.save(subtask);
        return subtaskMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteSubtask(UUID id) {
        if (!subtaskRepository.existsById(id)) {
            throw new EntityNotFoundException("Subtask not found: " + id);
        }
        subtaskRepository.deleteById(id);
    }

    @Override
    public List<TimeslotResponse> getOccupiedSlots(UUID userId, Instant from, Instant to) {
        return timeslotRepository
                .findByUserIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(userId, from, to)
                .stream()
                .map(timeslotMapper::toResponse)
                .toList();
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    private Task findTaskOrThrow(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + id));
    }

    private Subtask findSubtaskOrThrow(UUID id) {
        return subtaskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subtask not found: " + id));
    }

    private CognitiveLoad parseCognitiveLoad(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return CognitiveLoad.valueOf(value);
    }
}

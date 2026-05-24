package io.hohichh.planning_assistant.service;

import io.hohichh.planning_assistant.dto.SubtaskCreateRequest;
import io.hohichh.planning_assistant.dto.SubtaskResponse;
import io.hohichh.planning_assistant.dto.SubtaskUpdateRequest;
import io.hohichh.planning_assistant.dto.TaskRequest;
import io.hohichh.planning_assistant.dto.TaskResponse;
import io.hohichh.planning_assistant.dto.TimeslotResponse;
import io.hohichh.planning_assistant.enums.SubtaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CalendarService {

    TaskResponse createTask(UUID userId, TaskRequest request);

    List<TaskResponse> getTasks(UUID userId);

    TaskResponse getTask(UUID id);

    TaskResponse updateTask(UUID id, TaskRequest request);

    void deleteTask(UUID id);

    SubtaskResponse createSubtask(UUID userId, SubtaskCreateRequest request);

    SubtaskResponse getSubtask(UUID id);

    List<SubtaskResponse> getSubtasksByTask(UUID taskId);

    SubtaskResponse updateSubtask(UUID id, SubtaskUpdateRequest request);

    SubtaskResponse updateStatus(UUID id, SubtaskStatus status);

    void deleteSubtask(UUID id);

    List<TimeslotResponse> getOccupiedSlots(UUID userId, Instant from, Instant to);
}

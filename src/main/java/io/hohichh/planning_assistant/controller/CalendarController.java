package io.hohichh.planning_assistant.controller;

import io.hohichh.planning_assistant.dto.StatusPatch;
import io.hohichh.planning_assistant.dto.SubtaskCreateRequest;
import io.hohichh.planning_assistant.dto.SubtaskResponse;
import io.hohichh.planning_assistant.dto.SubtaskUpdateRequest;
import io.hohichh.planning_assistant.dto.TaskRequest;
import io.hohichh.planning_assistant.dto.TaskResponse;
import io.hohichh.planning_assistant.dto.TimeslotResponse;
import io.hohichh.planning_assistant.enums.SubtaskStatus;
import io.hohichh.planning_assistant.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    // ===== Tasks =====

    @PostMapping("/tasks")
    public ResponseEntity<TaskResponse> createTask(@PathVariable UUID userId, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(calendarService.createTask(userId, request));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<TaskResponse>> getTasks(@PathVariable UUID userId) {
        return ResponseEntity.ok(calendarService.getTasks(userId));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable UUID taskId) {
        return ResponseEntity.ok(calendarService.getTask(taskId));
    }

    @PutMapping("/tasks/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable UUID taskId, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(calendarService.updateTask(taskId, request));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID taskId) {
        calendarService.deleteTask(taskId);
        return ResponseEntity.noContent().build();
    }

    // ===== Subtasks =====

    @PostMapping("/subtasks")
    public ResponseEntity<SubtaskResponse> createSubtask(@PathVariable UUID userId,
                                                         @Valid @RequestBody SubtaskCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(calendarService.createSubtask(userId, request));
    }

    @GetMapping("/subtasks/{subtaskId}")
    public ResponseEntity<SubtaskResponse> getSubtask(@PathVariable UUID subtaskId) {
        return ResponseEntity.ok(calendarService.getSubtask(subtaskId));
    }

    @GetMapping("/tasks/{taskId}/subtasks")
    public ResponseEntity<List<SubtaskResponse>> getSubtasksByTask(@PathVariable UUID taskId) {
        return ResponseEntity.ok(calendarService.getSubtasksByTask(taskId));
    }

    @PutMapping("/subtasks/{subtaskId}")
    public ResponseEntity<SubtaskResponse> updateSubtask(@PathVariable UUID subtaskId,
                                                         @Valid @RequestBody SubtaskUpdateRequest request) {
        return ResponseEntity.ok(calendarService.updateSubtask(subtaskId, request));
    }

    @PatchMapping("/subtasks/{subtaskId}/status")
    public ResponseEntity<SubtaskResponse> updateStatus(@PathVariable UUID subtaskId,
                                                        @Valid @RequestBody StatusPatch patch) {
        return ResponseEntity.ok(calendarService.updateStatus(subtaskId, SubtaskStatus.valueOf(patch.status())));
    }

    @DeleteMapping("/subtasks/{subtaskId}")
    public ResponseEntity<Void> deleteSubtask(@PathVariable UUID subtaskId) {
        calendarService.deleteSubtask(subtaskId);
        return ResponseEntity.noContent().build();
    }

    // ===== Timeslots =====

    @GetMapping("/timeslots")
    public ResponseEntity<List<TimeslotResponse>> getOccupiedSlots(@PathVariable UUID userId,
                                                                   @RequestParam Instant from,
                                                                   @RequestParam Instant to) {
        return ResponseEntity.ok(calendarService.getOccupiedSlots(userId, from, to));
    }
}

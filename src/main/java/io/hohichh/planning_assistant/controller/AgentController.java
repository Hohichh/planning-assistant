package io.hohichh.planning_assistant.controller;

import io.hohichh.planning_assistant.dto.AgentRequest;
import io.hohichh.planning_assistant.dto.DraftResponse;
import io.hohichh.planning_assistant.dto.SubtaskResponse;
import io.hohichh.planning_assistant.service.AgentService;
import io.hohichh.planning_assistant.service.DraftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final DraftService draftService;

    @PostMapping("/process")
    public ResponseEntity<DraftResponse> processRequest(@PathVariable UUID userId,
                                                        @Valid @RequestBody AgentRequest request) {
        return ResponseEntity.ok(agentService.processRequest(request.message(), userId, request.taskId()));
    }

    @GetMapping("/draft")
    public ResponseEntity<DraftResponse> getDraft(@PathVariable UUID userId) {
        return ResponseEntity.ok(draftService.getDraft(userId));
    }

    @PostMapping("/draft/commit")
    public ResponseEntity<List<SubtaskResponse>> commitDraft(@PathVariable UUID userId) {
        return ResponseEntity.ok(draftService.commitDraft(userId));
    }

    @DeleteMapping("/draft")
    public ResponseEntity<Void> rejectDraft(@PathVariable UUID userId) {
        draftService.rejectDraft(userId);
        return ResponseEntity.noContent().build();
    }
}

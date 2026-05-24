package io.hohichh.planning_assistant.service;

import io.hohichh.planning_assistant.dto.DraftResponse;

import java.util.UUID;

public interface AgentService {

    DraftResponse processRequest(String message, UUID userId, UUID taskId);
}

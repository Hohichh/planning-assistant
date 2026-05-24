package io.hohichh.planning_assistant.service;

import io.hohichh.planning_assistant.dto.AuthMethodRequest;
import io.hohichh.planning_assistant.dto.AuthMethodResponse;

import java.util.List;
import java.util.UUID;

public interface AuthService {

    AuthMethodResponse create(AuthMethodRequest request);

    AuthMethodResponse getById(UUID id);

    List<AuthMethodResponse> getByUserId(UUID userId);

    AuthMethodResponse update(UUID id, AuthMethodRequest request);

    void delete(UUID id);
}

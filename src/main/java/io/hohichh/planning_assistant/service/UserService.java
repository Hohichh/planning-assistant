package io.hohichh.planning_assistant.service;

import io.hohichh.planning_assistant.dto.UserRequest;
import io.hohichh.planning_assistant.dto.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse create(UserRequest request);

    UserResponse getById(UUID id);

    UserResponse update(UUID id, UserRequest request);

    void delete(UUID id);
}

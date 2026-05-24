package io.hohichh.planning_assistant.service.impl;

import io.hohichh.planning_assistant.dto.AuthMethodRequest;
import io.hohichh.planning_assistant.dto.AuthMethodResponse;
import io.hohichh.planning_assistant.mapping.AuthMethodMapper;
import io.hohichh.planning_assistant.model.AuthMethod;
import io.hohichh.planning_assistant.model.User;
import io.hohichh.planning_assistant.repository.AuthMethodRepository;
import io.hohichh.planning_assistant.repository.UserRepository;
import io.hohichh.planning_assistant.service.AuthService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final AuthMethodRepository authMethodRepository;
    private final UserRepository userRepository;
    private final AuthMethodMapper authMethodMapper;

    @Override
    @Transactional
    public AuthMethodResponse create(AuthMethodRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + request.userId()));

        AuthMethod authMethod = authMethodMapper.toEntity(request);
        authMethod.setUser(user);
        AuthMethod saved = authMethodRepository.save(authMethod);
        return authMethodMapper.toResponse(saved);
    }

    @Override
    public AuthMethodResponse getById(UUID id) {
        AuthMethod authMethod = findOrThrow(id);
        return authMethodMapper.toResponse(authMethod);
    }

    @Override
    public List<AuthMethodResponse> getByUserId(UUID userId) {
        return authMethodRepository.findByUserId(userId).stream()
                .map(authMethodMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AuthMethodResponse update(UUID id, AuthMethodRequest request) {
        AuthMethod authMethod = findOrThrow(id);
        authMethodMapper.updateEntity(request, authMethod);
        AuthMethod saved = authMethodRepository.save(authMethod);
        return authMethodMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!authMethodRepository.existsById(id)) {
            throw new EntityNotFoundException("AuthMethod not found: " + id);
        }
        authMethodRepository.deleteById(id);
    }

    private AuthMethod findOrThrow(UUID id) {
        return authMethodRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("AuthMethod not found: " + id));
    }
}

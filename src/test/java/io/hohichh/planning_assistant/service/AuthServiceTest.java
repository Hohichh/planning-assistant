package io.hohichh.planning_assistant.service;

import io.hohichh.planning_assistant.dto.AuthMethodRequest;
import io.hohichh.planning_assistant.dto.AuthMethodResponse;
import io.hohichh.planning_assistant.mapping.AuthMethodMapper;
import io.hohichh.planning_assistant.model.AuthMethod;
import io.hohichh.planning_assistant.model.User;
import io.hohichh.planning_assistant.repository.AuthMethodRepository;
import io.hohichh.planning_assistant.repository.UserRepository;
import io.hohichh.planning_assistant.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthMethodRepository authMethodRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthMethodMapper authMethodMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    private UUID userId;
    private UUID authId;
    private User user;
    private AuthMethod authMethod;
    private AuthMethodRequest request;
    private AuthMethodResponse response;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        authId = UUID.randomUUID();
        user = User.builder().id(userId).name("Alice").build();
        authMethod = AuthMethod.builder()
                .id(authId)
                .user(user)
                .provider("local")
                .externalId("alice@example.com")
                .createdAt(Instant.now())
                .build();
        request = new AuthMethodRequest(userId, "local", "alice@example.com", null);
        response = new AuthMethodResponse(authId, userId, "local", "alice@example.com", authMethod.getCreatedAt());
    }

    @Test
    void create_shouldSaveAndReturn() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authMethodMapper.toEntity(request)).thenReturn(authMethod);
        when(authMethodRepository.save(authMethod)).thenReturn(authMethod);
        when(authMethodMapper.toResponse(authMethod)).thenReturn(response);

        AuthMethodResponse result = authService.create(request);

        assertNotNull(result);
        assertEquals("local", result.provider());
        verify(authMethodRepository).save(authMethod);
    }

    @Test
    void getById_shouldReturnResponse() {
        when(authMethodRepository.findById(authId)).thenReturn(Optional.of(authMethod));
        when(authMethodMapper.toResponse(authMethod)).thenReturn(response);

        AuthMethodResponse result = authService.getById(authId);

        assertEquals(authId, result.id());
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(authMethodRepository.findById(authId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authService.getById(authId));
    }

    @Test
    void getByUserId_shouldReturnList() {
        when(authMethodRepository.findByUserId(userId)).thenReturn(List.of(authMethod));
        when(authMethodMapper.toResponse(authMethod)).thenReturn(response);

        List<AuthMethodResponse> result = authService.getByUserId(userId);

        assertEquals(1, result.size());
        assertEquals("local", result.getFirst().provider());
    }

    @Test
    void delete_shouldInvokeRepository() {
        when(authMethodRepository.existsById(authId)).thenReturn(true);
        doNothing().when(authMethodRepository).deleteById(authId);

        authService.delete(authId);

        verify(authMethodRepository).deleteById(authId);
    }
}

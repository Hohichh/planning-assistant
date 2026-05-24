package io.hohichh.planning_assistant.service;

import io.hohichh.planning_assistant.dto.UserRequest;
import io.hohichh.planning_assistant.dto.UserResponse;
import io.hohichh.planning_assistant.mapping.UserMapper;
import io.hohichh.planning_assistant.model.User;
import io.hohichh.planning_assistant.repository.UserRepository;
import io.hohichh.planning_assistant.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private UUID userId;
    private User user;
    private UserRequest request;
    private UserResponse response;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .name("Alice")
                .createdAt(Instant.now())
                .build();
        request = new UserRequest("Alice");
        response = new UserResponse(userId, "Alice", user.getCreatedAt());
    }

    @Test
    void create_shouldSaveAndReturnResponse() {
        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse result = userService.create(request);

        assertNotNull(result);
        assertEquals("Alice", result.name());
        verify(userRepository).save(user);
    }

    @Test
    void getById_shouldReturnResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse result = userService.getById(userId);

        assertEquals(userId, result.id());
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.getById(userId));
    }

    @Test
    void update_shouldUpdateAndReturnResponse() {
        UserRequest updateReq = new UserRequest("Bob");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        doNothing().when(userMapper).updateEntity(updateReq, user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(
                new UserResponse(userId, "Bob", user.getCreatedAt()));

        UserResponse result = userService.update(userId, updateReq);

        assertEquals("Bob", result.name());
        verify(userMapper).updateEntity(updateReq, user);
    }

    @Test
    void delete_shouldInvokeRepository() {
        when(userRepository.existsById(userId)).thenReturn(true);
        doNothing().when(userRepository).deleteById(userId);

        userService.delete(userId);

        verify(userRepository).deleteById(userId);
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> userService.delete(userId));
    }
}

package io.hohichh.planning_assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hohichh.planning_assistant.dto.DraftResponse;
import io.hohichh.planning_assistant.dto.SubtaskResponse;
import io.hohichh.planning_assistant.model.Subtask;
import io.hohichh.planning_assistant.model.Task;
import io.hohichh.planning_assistant.model.User;
import io.hohichh.planning_assistant.repository.SubtaskRepository;
import io.hohichh.planning_assistant.repository.TaskRepository;
import io.hohichh.planning_assistant.repository.TimeslotRepository;
import io.hohichh.planning_assistant.repository.UserRepository;
import io.hohichh.planning_assistant.service.impl.AgentServiceImpl;
import io.hohichh.planning_assistant.service.strategy.SlotAllocationStrategy;
import io.hohichh.planning_assistant.valueObj.AllocationConstraints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock private DraftService draftService;
    @Mock private CalendarService calendarService;
    @Mock private TaskRepository taskRepository;
    @Mock private SubtaskRepository subtaskRepository;
    @Mock private TimeslotRepository timeslotRepository;
    @Mock private UserRepository userRepository;
    @Mock private SlotAllocationStrategy slotAllocationStrategy;
    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private ChatClient chatClient;
    @Mock private VectorStore vectorStore;
    @Mock private ChatMemory chatMemory;

    private AgentServiceImpl agentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        agentService = new AgentServiceImpl(
                chatClientBuilder, draftService, calendarService,
                taskRepository, subtaskRepository, timeslotRepository,
                userRepository,
                slotAllocationStrategy, objectMapper, vectorStore, chatMemory
        );
    }

    @Test
    void processRequest_validLlmResponse_createsDraftBatch() {
        User user = User.builder().id(USER_ID).name("Test").build();
        Task task = Task.builder().id(TASK_ID).user(user)
                .deadline(Instant.now().plusSeconds(86400 * 5)).build();

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.findByUserId(USER_ID)).thenReturn(List.of(task));
        when(subtaskRepository.findByTaskIdOrderBySortOrder(TASK_ID)).thenReturn(List.of());
        when(calendarService.getOccupiedSlots(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(vectorStore.similaritySearch(any(String.class))).thenReturn(List.of(new Document("context")));
        when(chatMemory.get(any(String.class))).thenReturn(List.of());

        String llmJson = """
                {
                  "subtasks": [
                    {"title": "Read docs", "estimatedMinutes": 30, "cognitiveLoad": "MEDIUM"},
                    {"title": "Write code", "estimatedMinutes": 60, "cognitiveLoad": "HIGH"}
                  ],
                  "constraints": {
                    "deadlineSemantic": "NONE",
                    "deadlineRawText": null,
                    "priority": "MEDIUM",
                    "preferredTimeOfDay": "ANY",
                    "avoidWeekends": false,
                    "maxDailyMinutes": null
                  }
                }
                """;

        mockChatClientResponse(llmJson);

        when(timeslotRepository.findByUserIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                eq(USER_ID), any(), any())).thenReturn(List.of());
        when(slotAllocationStrategy.allocate(anyList(), anyList(), any(AllocationConstraints.class)))
                .thenReturn(List.of());

        DraftResponse expectedResponse = new DraftResponse(
                List.of(mock(SubtaskResponse.class)), false);
        when(draftService.createDraftBatch(eq(USER_ID), anyList())).thenReturn(expectedResponse);

        DraftResponse result = agentService.processRequest("Plan my task", USER_ID, TASK_ID);

        assertNotNull(result);
        verify(draftService).createDraftBatch(eq(USER_ID), anyList());
    }

    @Test
    void processRequest_llmReturnsMarkdownWrapped_parsesCorrectly() {
        User user = User.builder().id(USER_ID).name("Test").build();
        Task task = Task.builder().id(TASK_ID).user(user)
                .deadline(Instant.now().plusSeconds(86400 * 5)).build();

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.findByUserId(USER_ID)).thenReturn(List.of(task));
        when(subtaskRepository.findByTaskIdOrderBySortOrder(TASK_ID)).thenReturn(List.of());
        when(calendarService.getOccupiedSlots(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(vectorStore.similaritySearch(any(String.class))).thenReturn(List.of());
        when(chatMemory.get(any(String.class))).thenReturn(List.of());

        String llmJson = """
                ```json
                {
                  "subtasks": [
                    {"title": "Study", "estimatedMinutes": 45, "cognitiveLoad": "HIGH"}
                  ],
                  "constraints": {
                    "deadlineSemantic": "NONE",
                    "deadlineRawText": null,
                    "priority": "LOW",
                    "preferredTimeOfDay": "MORNING",
                    "avoidWeekends": true,
                    "maxDailyMinutes": 120
                  }
                }
                ```
                """;

        mockChatClientResponse(llmJson);

        when(timeslotRepository.findByUserIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                eq(USER_ID), any(), any())).thenReturn(List.of());
        when(slotAllocationStrategy.allocate(anyList(), anyList(), any(AllocationConstraints.class)))
                .thenReturn(List.of());

        DraftResponse expectedResponse = new DraftResponse(List.of(), false);
        when(draftService.createDraftBatch(eq(USER_ID), anyList())).thenReturn(expectedResponse);

        DraftResponse result = agentService.processRequest("Help me", USER_ID, TASK_ID);

        assertNotNull(result);
        verify(draftService).createDraftBatch(eq(USER_ID), anyList());
    }

    @Test
    void processRequest_taskNotFound_throwsException() {
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

        assertThrows(Exception.class,
                () -> agentService.processRequest("Plan", USER_ID, TASK_ID));
    }

    @Test
    void processRequest_emptyLlmResponse_throwsIllegalState() {
        User user = User.builder().id(USER_ID).name("Test").build();
        Task task = Task.builder().id(TASK_ID).user(user)
                .deadline(Instant.now().plusSeconds(86400)).build();

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.findByUserId(USER_ID)).thenReturn(List.of(task));
        when(subtaskRepository.findByTaskIdOrderBySortOrder(TASK_ID)).thenReturn(List.of());
        when(calendarService.getOccupiedSlots(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(vectorStore.similaritySearch(any(String.class))).thenReturn(List.of());
        when(chatMemory.get(any(String.class))).thenReturn(List.of());

        mockChatClientResponse("");
        when(timeslotRepository.findByUserIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                eq(USER_ID), any(), any())).thenReturn(List.of());
        when(slotAllocationStrategy.allocate(anyList(), anyList(), any(AllocationConstraints.class)))
                .thenReturn(List.of());
        when(draftService.createDraftBatch(eq(USER_ID), anyList())).thenReturn(new DraftResponse(List.of(), false));

        DraftResponse response = agentService.processRequest("Plan", USER_ID, TASK_ID);

        assertNotNull(response);
        verify(draftService).createDraftBatch(eq(USER_ID), anyList());
    }

    @SuppressWarnings("unchecked")
    private void mockChatClientResponse(String content) {
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        CallResponseSpec callSpec = mock(CallResponseSpec.class);

        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        lenient().when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callSpec);
        lenient().when(callSpec.content()).thenReturn(content);
    }
}

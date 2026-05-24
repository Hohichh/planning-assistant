package io.hohichh.planning_assistant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hohichh.planning_assistant.dto.DraftResponse;
import io.hohichh.planning_assistant.dto.TimeslotResponse;
import io.hohichh.planning_assistant.dto.llm.LlmConstraints;
import io.hohichh.planning_assistant.dto.llm.LlmPlannerResponse;
import io.hohichh.planning_assistant.dto.llm.LlmSubtaskItem;
import io.hohichh.planning_assistant.enums.CognitiveLoad;
import io.hohichh.planning_assistant.enums.SubtaskStatus;
import io.hohichh.planning_assistant.enums.TaskStatus;
import io.hohichh.planning_assistant.exception.LlmServiceException;
import io.hohichh.planning_assistant.model.Subtask;
import io.hohichh.planning_assistant.model.Task;
import io.hohichh.planning_assistant.model.Timeslot;
import io.hohichh.planning_assistant.model.User;
import io.hohichh.planning_assistant.repository.SubtaskRepository;
import io.hohichh.planning_assistant.repository.TaskRepository;
import io.hohichh.planning_assistant.repository.TimeslotRepository;
import io.hohichh.planning_assistant.repository.UserRepository;
import io.hohichh.planning_assistant.service.AgentService;
import io.hohichh.planning_assistant.service.CalendarService;
import io.hohichh.planning_assistant.service.DraftService;
import io.hohichh.planning_assistant.service.strategy.SlotAllocationStrategy;
import io.hohichh.planning_assistant.valueObj.AllocationConstraints;
import io.hohichh.planning_assistant.valueObj.ProposedTimeslot;
import io.hohichh.planning_assistant.valueObj.SubtaskDraft;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    private final ChatClient chatClient;
    private final DraftService draftService;
    private final CalendarService calendarService;
    private final TaskRepository taskRepository;
    private final SubtaskRepository subtaskRepository;
    private final TimeslotRepository timeslotRepository;
    private final UserRepository userRepository;
    private final SlotAllocationStrategy slotAllocationStrategy;
    private final ObjectMapper objectMapper;
    private final VectorStore vectorStore;
    private final ChatMemory chatMemory;

    @Value("classpath:prompts/planner-system.txt")
    private Resource systemPromptResource;

    @Value("${planning.ai.timeout-ms:15000}")
    private long timeoutMs;

    @Value("${planning.ai.max-attempts:3}")
    private int maxAttempts;

    @Value("${planning.ai.retry-backoff-ms:500}")
    private long retryBackoffMs;

    public AgentServiceImpl(ChatClient.Builder chatClientBuilder,
                            DraftService draftService,
                            CalendarService calendarService,
                            TaskRepository taskRepository,
                            SubtaskRepository subtaskRepository,
                            TimeslotRepository timeslotRepository,
                            UserRepository userRepository,
                            SlotAllocationStrategy slotAllocationStrategy,
                            ObjectMapper objectMapper,
                            VectorStore vectorStore,
                            ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder.build();
        this.draftService = draftService;
        this.calendarService = calendarService;
        this.taskRepository = taskRepository;
        this.subtaskRepository = subtaskRepository;
        this.timeslotRepository = timeslotRepository;
        this.userRepository = userRepository;
        this.slotAllocationStrategy = slotAllocationStrategy;
        this.objectMapper = objectMapper;
        this.vectorStore = vectorStore;
        this.chatMemory = chatMemory;
    }

    @Override
    public DraftResponse processRequest(String message, UUID userId, UUID taskId) {
        Task task = taskId != null
                ? taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId))
                : null;

        String scheduleContext = buildScheduleContext(userId, task);
        String conversationId = conversationId(userId, taskId);
        reindexSemanticContext(userId, task);
        String semanticContext = buildSemanticContext(userId, task, message);
        String memoryContext = buildMemoryContext(conversationId);
        String currentDatetime = Instant.now().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        LlmPlannerResponse plannerResponse = invokeLlmWithRetry(
                message, taskId, scheduleContext, semanticContext, memoryContext, currentDatetime, task
        );

        LlmConstraints llmConstraints = plannerResponse.constraints();
        if (task == null) {
            task = createTaskFromPlannerResponse(userId, message, plannerResponse);
        }

        List<SubtaskDraft> drafts = toDrafts(plannerResponse.subtasks(), task.getId());
        AllocationConstraints constraints = toAllocationConstraints(llmConstraints, task);

        List<Timeslot> occupied = timeslotRepository
                .findByUserIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                        userId, constraints.rangeStart(), constraints.rangeEnd());

        List<ProposedTimeslot> proposedSlots = allocateSlotsSafely(drafts, occupied, constraints);

        drafts = mergeTimeslots(drafts, proposedSlots);

        chatMemory.add(conversationId, List.of(
                new UserMessage(message),
                new AssistantMessage(renderAssistantMemory(plannerResponse))
        ));

        return draftService.createDraftBatch(userId, drafts);
    }

    private LlmPlannerResponse invokeLlmWithRetry(String message,
                                                  UUID taskId,
                                                  String scheduleContext,
                                                  String semanticContext,
                                                  String memoryContext,
                                                  String currentDatetime,
                                                  Task task) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String rawResponse = CompletableFuture.supplyAsync(() -> chatClient.prompt()
                                .system(s -> s.text(systemPromptResource)
                                        .param("CURRENT_DATETIME", currentDatetime)
                                        .param("TASK_ID_OR_NULL", taskId != null ? taskId.toString() : "null")
                                        .param("SCHEDULE_CONTEXT", scheduleContext)
                                        .param("SEMANTIC_CONTEXT", semanticContext)
                                        .param("CHAT_MEMORY_CONTEXT", memoryContext))
                                .user(message)
                                .call()
                                .content())
                        .get(timeoutMs, TimeUnit.MILLISECONDS);

                return parseResponse(rawResponse);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmServiceException("AI planning request was interrupted", e);
            } catch (TimeoutException | ExecutionException | RuntimeException e) {
                lastException = e instanceof ExecutionException executionException
                        ? (Exception) executionException.getCause()
                        : e;
                log.warn("AI planning attempt {} of {} failed", attempt, maxAttempts, e);
                sleepBackoff(attempt);
            }
        }

        log.warn("Falling back to deterministic draft generation for task {}", task != null ? task.getId() : null, lastException);
        return fallbackPlan(message, task);
    }

    private void reindexSemanticContext(UUID userId, Task currentTask) {
        List<Task> tasks = taskRepository.findByUserId(userId);
        List<Document> documents = new ArrayList<>();
        List<String> idsToReplace = new ArrayList<>();

        for (Task task : tasks) {
            String taskDocId = "task-" + task.getId();
            idsToReplace.add(taskDocId);
            documents.add(new Document(taskDocId, buildTaskDocumentText(task), taskMetadata(userId, task)));

            List<Subtask> subtasks = subtaskRepository.findByTaskIdOrderBySortOrder(task.getId());
            for (Subtask subtask : subtasks) {
                String subtaskDocId = "subtask-" + subtask.getId();
                idsToReplace.add(subtaskDocId);
                documents.add(new Document(subtaskDocId, buildSubtaskDocumentText(subtask, task), subtaskMetadata(userId, task, subtask)));
            }
        }

        if (!idsToReplace.isEmpty()) {
            vectorStore.delete(idsToReplace);
        }
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }

    private String buildSemanticContext(UUID userId, Task task, String message) {
        try {
            String query = task != null
                    ? "user=" + userId + " task=" + task.getTitle() + " request=" + message
                    : "user=" + userId + " request=" + message;
            List<Document> documents = vectorStore.similaritySearch(query);
            if (documents == null || documents.isEmpty()) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            int limit = Math.min(documents.size(), 5);
            for (int i = 0; i < limit; i++) {
                Document document = documents.get(i);
                builder.append("[DOC ").append(i + 1).append("] ")
                        .append(document.getText())
                        .append("\n");
            }
            return builder.toString();
        } catch (RuntimeException ex) {
            log.warn("Semantic retrieval failed, continuing without vector context", ex);
            return "";
        }
    }

    private String buildMemoryContext(String conversationId) {
        List<Message> messages = chatMemory.get(conversationId);
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (Message message : messages) {
            if (message instanceof org.springframework.ai.chat.messages.AbstractMessage abstractMessage) {
                builder.append(abstractMessage.getMessageType())
                        .append(": ")
                        .append(abstractMessage.getText())
                        .append("\n");
            }
        }
        return builder.toString();
    }

    private String renderAssistantMemory(LlmPlannerResponse plannerResponse) {
        if (plannerResponse == null || plannerResponse.subtasks() == null || plannerResponse.subtasks().isEmpty()) {
            return "Не удалось сформировать подзадачи.";
        }
        StringBuilder builder = new StringBuilder("Сгенерированы подзадачи: ");
        for (int i = 0; i < plannerResponse.subtasks().size(); i++) {
            LlmSubtaskItem item = plannerResponse.subtasks().get(i);
            if (i > 0) {
                builder.append("; ");
            }
            builder.append(item.title()).append(" (").append(item.estimatedMinutes()).append(" мин)");
        }
        return builder.toString();
    }

    private String conversationId(UUID userId, UUID taskId) {
        return userId + ":" + (taskId != null ? taskId : "new");
    }

    private List<ProposedTimeslot> allocateSlotsSafely(List<SubtaskDraft> drafts,
                                                       List<Timeslot> occupied,
                                                       AllocationConstraints constraints) {
        try {
            List<Subtask> transientSubtasks = drafts.stream()
                    .map(this::toTransientSubtask)
                    .toList();
            return slotAllocationStrategy.allocate(transientSubtasks, occupied, constraints);
        } catch (RuntimeException ex) {
            log.warn("Timeslot allocation failed, continuing with draft subtasks without slots", ex);
            return List.of();
        }
    }

    private String buildScheduleContext(UUID userId, Task task) {
        Instant now = Instant.now();
        Instant rangeTo = task != null && task.getDeadline() != null ? task.getDeadline() : now.plusSeconds(7 * 86400);
        List<TimeslotResponse> slots = calendarService.getOccupiedSlots(userId, now, rangeTo);

        if (slots.isEmpty()) {
            return "Календарь пользователя пуст на период до дедлайна.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Занятые слоты (").append(slots.size()).append(" шт.):\n");
        for (TimeslotResponse slot : slots) {
            sb.append("- ").append(slot.startTime()).append(" — ").append(slot.endTime()).append("\n");
        }
        return sb.toString();
    }

    private LlmPlannerResponse parseResponse(String raw) {
        String json = extractJson(raw);
        try {
            return objectMapper.readValue(json, LlmPlannerResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", raw, e);
            throw new IllegalStateException("LLM returned unparseable response", e);
        }
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("LLM returned empty response");
        }
        String trimmed = raw.strip();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.strip();
    }

    private LlmPlannerResponse fallbackPlan(String message, Task task) {
        String title = task != null && task.getTitle() != null && !task.getTitle().isBlank()
                ? "Сделать первый рабочий шаг по задаче: " + task.getTitle()
                : "Разобрать запрос и сформировать первый шаг";

        LlmSubtaskItem item = new LlmSubtaskItem(
                title,
                45,
                CognitiveLoad.MEDIUM.name()
        );

        LlmConstraints constraints = new LlmConstraints(
                task != null ? "NONE" : "NEXT_WEEK",
                null,
                "MEDIUM",
                "ANY",
                false,
                null
        );

        return new LlmPlannerResponse(task != null ? null : message, List.of(item), constraints);
    }

    private Task createTaskFromPlannerResponse(UUID userId, String message, LlmPlannerResponse plannerResponse) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        LlmConstraints constraints = plannerResponse.constraints();
        String title = plannerResponse.taskTitle() != null && !plannerResponse.taskTitle().isBlank()
                ? plannerResponse.taskTitle()
                : message;
        Task task = Task.builder()
                .user(user)
                .title(title)
                .description(message)
                .deadline(calculateDeadline(
                        constraints != null ? constraints.deadlineSemantic() : null,
                        constraints != null ? constraints.deadlineRawText() : null
                ))
                .status(TaskStatus.ACTIVE)
                .build();
        return taskRepository.save(task);
    }

    private Instant calculateDeadline(String semantic, String rawText) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String normalized = semantic == null || semantic.isBlank() ? "NONE" : semantic.toUpperCase();
        return switch (normalized) {
            case "TODAY" -> LocalDateTime.of(now.toLocalDate(), LocalTime.MAX).toInstant(ZoneOffset.UTC);
            case "TOMORROW" -> LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.MAX).toInstant(ZoneOffset.UTC);
            case "END_OF_WEEK" -> LocalDateTime.of(
                    now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
                    LocalTime.MAX
            ).toInstant(ZoneOffset.UTC);
            case "NEXT_WEEK" -> LocalDateTime.of(now.toLocalDate().plusWeeks(1), LocalTime.MAX).toInstant(ZoneOffset.UTC);
            case "END_OF_MONTH" -> LocalDateTime.of(
                    now.toLocalDate().with(TemporalAdjusters.lastDayOfMonth()),
                    LocalTime.MAX
            ).toInstant(ZoneOffset.UTC);
            case "CUSTOM_DATE" -> parseCustomDeadline(rawText, now);
            default -> LocalDateTime.of(now.toLocalDate().plusWeeks(1), LocalTime.MAX).toInstant(ZoneOffset.UTC);
        };
    }

    private Instant parseCustomDeadline(String rawText, LocalDateTime now) {
        if (rawText != null && !rawText.isBlank()) {
            String value = rawText.strip();
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
            } catch (RuntimeException ignored) {
            }
            try {
                return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
                        .atTime(LocalTime.MAX)
                        .toInstant(ZoneOffset.UTC);
            } catch (RuntimeException ignored) {
            }
        }
        return LocalDateTime.of(now.toLocalDate().plusWeeks(1), LocalTime.MAX).toInstant(ZoneOffset.UTC);
    }

    private String buildTaskDocumentText(Task task) {
        return "Task: " + safe(task.getTitle())
                + "\nDescription: " + safe(task.getDescription())
                + "\nDeadline: " + (task.getDeadline() != null ? task.getDeadline() : "none")
                + "\nStatus: " + (task.getStatus() != null ? task.getStatus() : "UNKNOWN");
    }

    private String buildSubtaskDocumentText(Subtask subtask, Task task) {
        return "Subtask: " + safe(subtask.getTitle())
                + "\nTask: " + safe(task.getTitle())
                + "\nEstimated minutes: " + subtask.getEstimatedMinutes()
                + "\nCognitive load: " + (subtask.getCognitiveLoad() != null ? subtask.getCognitiveLoad() : "UNKNOWN")
                + "\nStatus: " + (subtask.getStatus() != null ? subtask.getStatus() : "UNKNOWN");
    }

    private Map<String, Object> taskMetadata(UUID userId, Task task) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", userId.toString());
        metadata.put("taskId", task.getId().toString());
        metadata.put("kind", "task");
        return metadata;
    }

    private Map<String, Object> subtaskMetadata(UUID userId, Task task, Subtask subtask) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", userId.toString());
        metadata.put("taskId", task.getId().toString());
        metadata.put("subtaskId", subtask.getId().toString());
        metadata.put("kind", "subtask");
        return metadata;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<SubtaskDraft> toDrafts(List<LlmSubtaskItem> items, UUID taskId) {
        List<SubtaskDraft> drafts = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            LlmSubtaskItem item = items.get(i);
            drafts.add(new SubtaskDraft(
                    taskId,
                    item.title(),
                    item.estimatedMinutes(),
                    parseCognitiveLoad(item.cognitiveLoad()),
                    i + 1,
                    null,
                    null
            ));
        }
        return drafts;
    }

    private Subtask toTransientSubtask(SubtaskDraft draft) {
        return Subtask.builder()
                .id(UUID.randomUUID())
                .title(draft.title())
                .estimatedMinutes(draft.estimatedMinutes())
                .cognitiveLoad(draft.cognitiveLoad())
                .sortOrder(draft.sortOrder())
                .status(SubtaskStatus.DRAFT)
                .isCompleted(false)
                .build();
    }

    private AllocationConstraints toAllocationConstraints(LlmConstraints llm, Task task) {
        Instant now = Instant.now();
        Instant deadline = task.getDeadline();

        Instant rangeEnd = deadline != null ? deadline : now.plusSeconds(7 * 86400);

        return new AllocationConstraints(
                now,
                rangeEnd,
                90,
                10,
                deadline,
                llm != null && llm.preferredTimeOfDay() != null ? llm.preferredTimeOfDay() : "ANY",
                llm != null && Boolean.TRUE.equals(llm.avoidWeekends()),
                llm != null ? llm.maxDailyMinutes() : null
        );
    }

    private List<SubtaskDraft> mergeTimeslots(List<SubtaskDraft> drafts, List<ProposedTimeslot> slots) {
        if (slots.isEmpty()) {
            return drafts;
        }
        List<SubtaskDraft> merged = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            SubtaskDraft draft = drafts.get(i);
            if (i < slots.size()) {
                ProposedTimeslot slot = slots.get(i);
                merged.add(new SubtaskDraft(
                        draft.taskId(),
                        draft.title(),
                        draft.estimatedMinutes(),
                        draft.cognitiveLoad(),
                        draft.sortOrder(),
                        slot.startTime(),
                        slot.endTime()
                ));
            } else {
                merged.add(draft);
            }
        }
        return merged;
    }

    private CognitiveLoad parseCognitiveLoad(String value) {
        if (value == null || value.isBlank()) {
            return CognitiveLoad.MEDIUM;
        }
        try {
            return CognitiveLoad.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CognitiveLoad.MEDIUM;
        }
    }

    private void sleepBackoff(int attempt) {
        if (attempt >= maxAttempts || retryBackoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMs * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmServiceException("AI retry backoff interrupted", e);
        }
    }
}

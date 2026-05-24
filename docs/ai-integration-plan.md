# План интеграции AI в Planning Assistant

## 1. Обзор архитектуры

Текущий `AgentService.processRequest(message, userId, taskId)` — это stub.  
Задача: пользователь отправляет текстовое сообщение → LLM анализирует задачу → генерирует набор `SubtaskDraft` → через `DraftService.createDraftBatch()` создаёт черновик → пользователь approve/reject.

```
User ──► AgentController ──► AgentService ──► LLM Provider
                                   │
                                   ▼
                            DraftService.createDraftBatch()
                                   │
                                   ▼
                         SlotAllocationStrategy.allocate()
                                   │
                                   ▼
                            DraftResponse (subtasks + timeslots)
```

---

## 2. Пошаговый план реализации

### Шаг 1. Добавить зависимость Spring AI

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

Spring AI BOM уже включён в проект (`spring-ai-bom 2.0.0-M6`).  
Для других провайдеров используй соответствующий starter (см. таблицу ниже).

### Шаг 2. Конфигурация в `application.properties`

```properties
# ===== Spring AI =====
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o
spring.ai.openai.chat.options.temperature=0.3

# Для Anthropic:
# spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
# spring.ai.anthropic.chat.options.model=claude-sonnet-4-20250514
```

### Шаг 3. Создать системный промпт

Файл: `src/main/resources/prompts/planner-system.txt`

```
Ты — ассистент по планированию. Пользователь описывает задачу.  
Ты должен декомпозировать её на подзадачи и предложить слоты для каждой.

Ответь СТРОГО в формате JSON-массива:
[
  {
    "title": "...",
    "estimatedMinutes": 60,
    "cognitiveLoad": "LOW | MEDIUM | HIGH",
    "sortOrder": 1
  }
]

Контекст:
- Задача: {taskTitle}
- Дедлайн: {deadline}
- Существующие подзадачи: {existingSubtasks}
- Занятые слоты: {occupiedSlots}
```

### Шаг 4. Реализовать `AgentServiceImpl`

```java
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final ChatClient chatClient;
    private final DraftService draftService;
    private final CalendarService calendarService;
    private final TaskRepository taskRepository;
    
    @Value("classpath:prompts/planner-system.txt")
    private Resource systemPrompt;

    @Override
    public DraftResponse processRequest(String message, UUID userId, UUID taskId) {
        // 1. Собрать контекст
        Task task = taskRepository.findById(taskId).orElseThrow();
        List<SubtaskResponse> existing = calendarService.getSubtasksByTask(taskId);
        List<TimeslotResponse> occupied = calendarService.getOccupiedSlots(
            userId, Instant.now(), task.getDeadline());

        // 2. Вызвать LLM через Spring AI ChatClient
        //    с Structured Output (BeanOutputConverter)
        List<SubtaskDraft> drafts = chatClient.prompt()
            .system(s -> s.text(systemPrompt)
                .param("taskTitle", task.getTitle())
                .param("deadline", task.getDeadline().toString())
                .param("existingSubtasks", serialize(existing))
                .param("occupiedSlots", serialize(occupied)))
            .user(message)
            .call()
            .entity(new ParameterizedTypeReference<List<SubtaskDraft>>() {});

        // 3. (Опционально) Вызвать SlotAllocationStrategy для подбора слотов
        // 4. Создать черновик
        return draftService.createDraftBatch(userId, drafts);
    }
}
```

### Шаг 5. Реализовать `SlotAllocationStrategy`

```java
@Component
public class GreedySlotAllocationStrategy implements SlotAllocationStrategy {

    @Override
    public List<ProposedTimeslot> allocate(
            List<Subtask> subtasks,
            List<Timeslot> occupied,
            AllocationConstraints constraints) {
        // Жадный алгоритм:
        // 1. Отсортировать subtasks по приоритету / cognitive load
        // 2. Для каждого subtask найти первый свободный слот
        //    с учётом constraints (рабочие часы, перерывы, max часов в день)
        // 3. Вернуть List<ProposedTimeslot>
    }
}
```

### Шаг 6. Тесты

- **Unit-тесты**: мокнуть `ChatClient`, проверить парсинг ответа LLM
- **Integration-тесты**: WireMock для эмуляции OpenAI API
- **E2E**: Testcontainers + реальный PostgreSQL

### Шаг 7. Конфигурация API-ключей

```yaml
# docker-compose.yml
app:
  environment:
    OPENAI_API_KEY: ${OPENAI_API_KEY}
```

Никогда не хардкодь ключи. Используй переменные окружения или Vault.

---

## 3. Сравнение LLM-провайдеров

| Провайдер | Модель | Цена (input/output за 1M tokens) | Structured Output | Spring AI Starter | Плюсы | Минусы |
|-----------|--------|----------------------------------|-------------------|-------------------|-------|--------|
| **OpenAI** | `gpt-4o` | $2.50 / $10 | ✅ JSON Schema (native) | `spring-ai-starter-model-openai` | Лучший structured output, быстрый, огромная экосистема | Цена при больших объёмах |
| **OpenAI** | `gpt-4o-mini` | $0.15 / $0.60 | ✅ JSON Schema | `spring-ai-starter-model-openai` | Очень дёшево, хорош для простых задач | Хуже reasoning чем gpt-4o |
| **Anthropic** | `claude-sonnet-4-20250514` | $3 / $15 | ✅ Tool Use | `spring-ai-starter-model-anthropic` | Отличный reasoning, длинный контекст (200K) | Дороже gpt-4o |
| **Anthropic** | `Claude 3.5 Haiku` | $0.80 / $4 | ✅ Tool Use | `spring-ai-starter-model-anthropic` | Быстрый, дешевле Sonnet | Слабее в сложных задачах |
| **Google** | `Gemini 2.0 Flash` | $0.10 / $0.40 | ✅ JSON mode | `spring-ai-starter-model-vertex-ai` | Самый дешёвый, 1M context | Structured output менее надёжный |
| **Mistral** | `Mistral Large` | $2 / $6 | ✅ JSON mode | `spring-ai-starter-model-mistral-ai` | Хороший баланс цена/качество, EU data | Меньше экосистема |
| **Ollama** (local) | `llama3.1:8b` | Бесплатно | ⚠️ Промптом | `spring-ai-starter-model-ollama` | Полная приватность, бесплатно | Нужен GPU, качество ниже |
| **Groq** | `llama-3.3-70b` | $0.59 / $0.79 | ⚠️ JSON mode | Через OpenAI-совместимый API | Молниеносная скорость (>300 tok/s) | Лимиты на бесплатном плане |

---

## 4. Рекомендация

### Для разработки / прототипа
**Ollama + llama3.1:8b** — бесплатно, локально, не нужен API-ключ.  
Или **Groq** — бесплатный тир, очень быстрый.

### Для продакшена (бюджетный вариант)
**OpenAI gpt-4o-mini** — лучшее соотношение цена/качество, надёжный structured output.

### Для продакшена (максимальное качество)
**OpenAI gpt-4o** или **Anthropic Claude Sonnet 4** — зависит от предпочтений.

### Для максимальной приватности
**Ollama** с self-hosted моделью на своём сервере.

---

## 5. Порядок интеграции (чеклист)

- [x] Добавить Spring AI starter dependency в `pom.xml`
- [x] Настроить `application.properties` с API-ключом (OpenRouter + DeepSeek)
- [x] Создать системный промпт (`planner-system.txt`)
- [x] Реализовать `AgentServiceImpl` с `ChatClient`
- [x] JSON-парсинг LLM-ответа через `ObjectMapper` + `extractJson()` (markdown-safe)
- [x] Реализовать `GreedySlotAllocationStrategy`
- [x] Написать unit-тесты с мок-LLM (AgentServiceTest + GreedySlotAllocationStrategyTest)
- [x] Написать integration-тест с WireMock
- [x] Добавить `OPEN_ROUTER_API_KEY` в `docker-compose.yml` через env
- [x] Добавить контейнер с реляционным Postgres в `docker-compose.yml` и проверить datasource-конфиг в `application.properties`
- [x] Добавить error handling: retry, timeout, fallback при недоступности LLM
- [x] Добавить `GlobalExceptionHandler` advice для ошибок и хендлинг основных runtime-ошибок
- [x] Обернуть ответы контроллеров в `ResponseEntity`, ошибки отдавать через `ProblemDetail`
- [x] Добавить rate limiting на `/agent/process` endpoint
- [x] Добавить conversation memory через Spring AI `ChatMemory` - (это не векторка, а память диалога)

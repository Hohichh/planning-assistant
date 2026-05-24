# Архитектурное обсуждение
 
Этот документ фиксирует итоговые архитектурные решения по проекту и предназначен как краткий контракт для реализации и использования в качестве контекста для LLM.
 
- Диаграммы являются визуальным представлением архитектуры.
- Этот файл фиксирует ключевые решения, инварианты и границы ответственности.
- Для реализации authoritative source составляют актуальные диаграммы и данный summary.
- При конфликте между устаревшим черновым описанием и актуальными диаграммами следует опираться на актуальные диаграммы и данный summary.

## Системные инварианты

- У пользователя одновременно существует не более одного активного чернового набора изменений.
- Подтверждённые временные слоты одного пользователя не могут пересекаться.
- Черновые изменения не модифицируют подтверждённое расписание до явного `commit`.
- `sort_order` определяет порядок подзадач только в рамках одного `task_id`.
- `password_hash` никогда не возвращается наружу через API.
- В операциях календарного домена `user_id` должен браться из контекста авторизации, а не из клиентского request body.
- `timeslots` не являются самостоятельным пользовательским ресурсом CRUD и управляются через операции над подзадачами или через lifecycle черновика.

## Границы ответственности слоёв

### Controller

- Принимает HTTP-запросы.
- Выполняет маршрутизацию и первичную валидацию DTO.
- Извлекает пользователя из контекста авторизации.
- Не содержит бизнес-логики и не обращается к БД напрямую.

### Service

- Содержит бизнес-логику и orchestration.
- Управляет транзакциями.
- Проверяет доменные инварианты.
- Координирует работу репозиториев, алгоритмов размещения и внешних интеграций.

### Repository

- Отвечает только за доступ к данным.
- Не содержит orchestration-логики и сценариев уровня use case.
- Для реляционной части использует JPA-репозитории.

### Vector store

- Отвечает за семантический поиск и хранение эмбеддингов.
- Не является JPA-репозиторием.
- Используется как инфраструктурный компонент через `VectorStore`.

## Модель данных

### Пользователь и авторизация

- Используется модель `users` + `auth_methods` со связью 1:N.
- `auth_methods` поддерживает локальную авторизацию и внешние провайдеры OAuth/SSO.
- Для локального входа используется запись с `provider = 'local'`.
- Для идемпотентной интеграции с внешними провайдерами задаётся ограничение `UNIQUE(provider, external_id)`.

### Задачи, подзадачи и временные слоты

- `tasks` хранят долгосрочные учебные цели.
- `subtasks` являются атомарными шагами планирования и отображения в календаре.
- Порядок подзадач внутри задачи задаётся полем `sort_order`.
- `timeslots` вынесены в отдельную таблицу, что сохраняет готовность к внешним интеграциям и событиям без привязки к подзадаче.
- Поле `subtask_id` в `timeslots` допускает `NULL`.
- Поле `user_id` дублируется в `subtasks` и `timeslots` для фильтрации, ограничений целостности и пользовательских выборок.

### Черновик расписания

- Отдельная таблица черновиков не используется.
- Черновик реализуется через статусы подзадач: `draft`, `pending_delete`, `scheduled`, `completed`.
- Новые предложения агента создаются как `draft`.
- При модификации существующей подзадачи создаётся новая черновая подзадача, а исходная помечается как `pending_delete`.
- Связь между новой и заменяемой подзадачей задаётся через `replaces_id`.

## Итоговая схема модели данных

Основные сущности:

- `users`
- `auth_methods`
- `tasks`
- `subtasks`
- `timeslots`

Ключевые изменения относительно ранней версии модели:

- Удалена отдельная сущность `schedule_drafts`.
- Удалён `dependency_id` у подзадач; порядок теперь задаётся через `sort_order`.
- Данные авторизации вынесены из `users` в `auth_methods`.
- Добавлены `status` и `replaces_id` у `subtasks`.
- Добавлены `user_id` в `subtasks` и `timeslots`.
- `subtask_id` в `timeslots` сделан nullable.

## Дата-слой

### Репозитории и доступ к данным

Реляционная часть реализуется через отдельные репозитории для следующих сущностей:

- `UserRepository`
- `AuthMethodRepository`
- `TaskRepository`
- `SubtaskRepository`
- `TimeslotRepository`

Векторное хранилище не моделируется как JPA-репозиторий. Для него используется интерфейс `VectorStore` из Spring AI.

### Каскадные правила

| Связь | ON DELETE | ON UPDATE |
|---|---|---|
| `users` → `auth_methods` | CASCADE | CASCADE |
| `users` → `tasks` | CASCADE | CASCADE |
| `users` → `timeslots` | CASCADE | CASCADE |
| `tasks` → `subtasks` | CASCADE | CASCADE |
| `subtasks` → `timeslots` | CASCADE | CASCADE |
| `subtasks.replaces_id` → `subtasks` | SET NULL | CASCADE |

Это гарантирует целостность модели при удалении пользователя, задачи, подзадачи или её замещающей версии.

### Правила порядка и ограничений

- При агентной генерации подзадач `sort_order` назначается последовательно в рамках одной транзакции.
- При ручном добавлении в конец используется `max(sort_order) + 1` в рамках `task_id`.
- При вставке в середину затронутый диапазон подзадач сдвигается на `+1`.
- После удаления допускаются пропуски в `sort_order`; пересчёт не обязателен.

Для `timeslots` используется ограничение исключения PostgreSQL:

```sql
ALTER TABLE timeslots
ADD CONSTRAINT no_overlap
EXCLUDE USING gist (
  user_id WITH =,
  tsrange(start_time, end_time) WITH &&
) WHERE (is_committed = true);
```

Ограничение действует только для подтверждённых слотов. Черновые слоты не блокируют время. На уровне сервиса дополнительно выполняется pre-check для возврата понятной ошибки пользователю.

### Фильтрации и рабочие правила

- Подзадачи задачи читаются по `task_id` с сортировкой по `sort_order`.
- Подзадачи календаря фильтруются по `user_id` и `status`.
- Черновик читается по `user_id` и `status IN ('draft', 'pending_delete')`.
- Временные слоты фильтруются по `user_id`, диапазону дат и признаку `is_committed`.
- Поиск свободных окон реализуется в сервисном слое как алгоритм, а не как отдельный SQL-запрос.
- Для MVP используется hard delete; soft delete не применяется.

### Векторное хранилище

- Для MVP используется `pgvector` в PostgreSQL.
- Отдельная векторная БД не требуется.
- Интеграция реализуется на Java через Spring AI и `PgVectorStore`.
- Доступ к семантическому поиску идёт через `VectorStore` с операциями `add`, `similaritySearch`, `delete`.

## Сервис-слой

### Manual flow

Сервисный слой ручного календарного сценария состоит из трёх основных сервисов:

- `UserService` — CRUD пользователей.
- `AuthService` — CRUD методов входа и логика аутентификации.
- `CalendarService` — операции над задачами, подзадачами и временными слотами.

`CalendarService` объединяет функции задач, подзадач и временных слотов, поскольку это один домен с сильной связностью и общими транзакциями.

### Agentic flow

Агентный сценарий строится вокруг следующих компонентов:

- `AgentService` — оркестратор сценария генерации.
- `CalendarService` — источник календарного контекста и текущего расписания.
- `SlotAllocationStrategy` — интерфейс алгоритма размещения подзадач по слотам.
- `DraftService` — сервис жизненного цикла черновика.

Дополнительный `QueryService` не нужен: извлечение календарного контекста выполняется через `CalendarService`, а логика обогащения может оставаться внутри `AgentService`.

`SlotAllocationStrategy` определяет общий контракт вида:

`List<ProposedTimeslot> allocate(List<Subtask> subtasks, List<Timeslot> occupied, AllocationConstraints constraints)`

Возможны разные реализации стратегии, например жадная или учитывающая когнитивную нагрузку.

### DraftService

`DraftService` отвечает за конечный автомат черновика и инжектит `SubtaskRepository` и `TimeslotRepository`.

Основные операции:

- `createDraftBatch(userId, List<SubtaskDraft>)`
- `proposeDraftModification(userId, subtaskId, newTimeslot)`
- `commitDraft(userId)`
- `rejectDraft(userId)`
- `getDraft(userId)`

Инвариант сервиса: у пользователя одновременно существует только один активный черновой набор изменений.

### Взаимодействие с LLM

- `AgentService` принимает пользовательский запрос.
- Через `CalendarService` собирается контекст: занятые слоты, задачи, дедлайны, текущее время.
- При необходимости выполняется семантический поиск во векторном хранилище.
- В языковую модель отправляется обогащённый промпт.
- Модель возвращает структурированный JSON со списком подзадач.
- `AgentService` передаёт результат в `SlotAllocationStrategy`, затем в `DraftService`.

Для взаимодействия с моделью используется подход Structured Output. Отдельный класс для «инструментов» доступа к данным не требуется.

### UML-представление

Для диаграмм проектирования сервисы и репозитории показываются как интерфейсы. Между ними моделируются зависимости, а не агрегация или детали Spring DI.

## Правила транзакционности

- `CalendarService.createTask(...)` — одна транзакция для создания задачи.
- `CalendarService.createSubtask(...)` — одна транзакция для создания подзадачи и связанного временного слота.
- `CalendarService.deleteTask(...)` — одна транзакция с каскадным удалением дочерних подзадач и слотов.
- `DraftService.createDraftBatch(...)` — одна транзакция для сохранения всех черновых подзадач и слотов.
- `DraftService.commitDraft(...)` — одна транзакция для перевода `draft -> scheduled` и удаления `pending_delete`.
- `DraftService.rejectDraft(...)` — одна транзакция для удаления `draft` и возврата `pending_delete -> scheduled`.
- Любая операция, затрагивающая и `subtask`, и `timeslot`, должна быть атомарной.

## Соответствие диаграмм и кода

| Артефакт | Соответствие в коде |
|---|---|
| ER diagram | SQL-схема, миграции, JPA entity |
| Repository diagrams | интерфейсы репозиториев и связанные entity |
| Service diagrams | service interfaces и их реализации |
| Controller diagrams | REST controllers, request/response DTO |
| Sequence diagrams | orchestration-логика методов сервисного слоя |
| Use-case diagram | пользовательские сценарии уровня API и UI |

Этот mapping нужен для того, чтобы код следовал диаграммам без появления лишних промежуточных абстракций, не отражённых в архитектуре.

## Controller layer & API

Корневой префикс API: `/api/v1`.

### Пользователи

| Метод | Эндпоинт | Request body | Response body |
|---|---|---|---|
| POST | `/users` | `UserRequest` | `UserResponse` |
| GET | `/users/{id}` | — | `UserResponse` |
| PUT | `/users/{id}` | `UserRequest` | `UserResponse` |
| DELETE | `/users/{id}` | — | 204 No Content |

`UserRequest  { name: String }`

`UserResponse { id: UUID, name: String, created_at: Timestamp }`

### Методы входа

| Метод | Эндпоинт | Request body | Response body |
|---|---|---|---|
| POST | `/auth-methods` | `AuthMethodRequest` | `AuthMethodResponse` |
| GET | `/auth-methods/{id}` | — | `AuthMethodResponse` |
| GET | `/auth-methods?user_id=` | — | `List<AuthMethodResponse>` |
| PUT | `/auth-methods/{id}` | `AuthMethodRequest` | `AuthMethodResponse` |
| DELETE | `/auth-methods/{id}` | — | 204 No Content |

`AuthMethodRequest  { user_id: UUID, provider: String, external_id: String, password_hash?: String }`

`AuthMethodResponse { id: UUID, user_id: UUID, provider: String, external_id: String, created_at: Timestamp }`

`password_hash` никогда не возвращается в response.

### Календарный домен

#### Tasks

| Метод | Эндпоинт | Request body | Response body |
|---|---|---|---|
| POST | `/calendar/tasks` | `TaskRequest` | `TaskResponse` |
| GET | `/calendar/tasks` | — | `List<TaskResponse>` |
| GET | `/calendar/tasks/{id}` | — | `TaskResponse` |
| PUT | `/calendar/tasks/{id}` | `TaskRequest` | `TaskResponse` |
| DELETE | `/calendar/tasks/{id}` | — | 204 No Content |

`TaskRequest  { title: String, description?: String, deadline: Timestamp, status: String }`

`TaskResponse { id: UUID, user_id: UUID, title: String, description?: String, deadline: Timestamp, status: String, created_at: Timestamp }`

#### Subtasks

| Метод | Эндпоинт | Request body | Response body |
|---|---|---|---|
| POST | `/calendar/subtasks` | `SubtaskCreateRequest` | `SubtaskResponse` |
| GET | `/calendar/subtasks/{id}` | — | `SubtaskResponse` |
| GET | `/calendar/subtasks?task_id=` | — | `List<SubtaskResponse>` |
| PUT | `/calendar/subtasks/{id}` | `SubtaskUpdateRequest` | `SubtaskResponse` |
| PATCH | `/calendar/subtasks/{id}/status` | `StatusPatch` | `SubtaskResponse` |
| DELETE | `/calendar/subtasks/{id}` | — | 204 No Content |

`SubtaskCreateRequest { task_id: UUID, title: String, estimated_minutes: Int, cognitive_load?: String, sort_order?: Int, start_time?: Timestamp, end_time?: Timestamp }`

`SubtaskUpdateRequest { title: String, estimated_minutes: Int, cognitive_load?: String, sort_order?: Int }`

`SubtaskResponse      { id: UUID, task_id: UUID, user_id: UUID, title: String, estimated_minutes: Int, cognitive_load?: String, sort_order: Int, is_completed: Boolean, status: String, replaces_id?: UUID, created_at: Timestamp, timeslot?: TimeslotResponse }`

`StatusPatch          { status: String }`

При создании подзадачи временной слот может создаваться в той же транзакции. Если `sort_order` не передан, используется позиция `max + 1`.

#### Timeslots

| Метод | Эндпоинт | Request body | Response body |
|---|---|---|---|
| GET | `/calendar/timeslots?from=&to=` | — | `List<TimeslotWithSubtask>` |

`TimeslotResponse     { id: UUID, subtask_id?: UUID, user_id: UUID, start_time: Timestamp, end_time: Timestamp, is_committed: Boolean }`

`TimeslotWithSubtask  { ...TimeslotResponse, subtask?: SubtaskResponse }`

Отдельные CRUD-эндпоинты для `timeslots` не выносятся: слоты создаются и удаляются вместе с подзадачами. GET используется для рендера календарной сетки.

### Агентный модуль

| Метод | Эндпоинт | Request body | Response body |
|---|---|---|---|
| POST | `/agent/generate` | `AgentRequest` | `DraftResponse` |
| POST | `/agent/draft/commit` | — | `List<SubtaskResponse>` |
| POST | `/agent/draft/reject` | — | 204 No Content |
| GET | `/agent/draft` | — | `DraftResponse` |

`AgentRequest  { message: String, task_id?: UUID }`

`DraftResponse { subtasks: List<SubtaskResponse>, has_modifications: Boolean }`

`DraftResponse` содержит подзадачи со статусами `draft` и `pending_delete` для текущего пользователя. Поле `has_modifications` показывает, что агент предложил изменение уже существующих подзадач.
# Final System Design: Traceable Task Ledger

## 1. Product Philosophy

The app is a **log-first task system**, not a traditional mutable task manager.

Traditional tools usually treat a task as a single global state:

```text
Task = current title + current status + current metadata
```

This causes a historical distortion: when a task is completed today, older daily journals also appear as if the task is completed.

This system avoids that by treating work as a chronological ledger.

Core idea:

```text
Task identity is stable.
Task lifecycle is append-only.
Daily journals are historical records.
Current status is derived, not stored.
```

The uploaded design already states the most important rule correctly: the `task` table must have **no `status` column**, and status should be derived from `task_event` records.

---

# 2. Core Requirements

## 2.1 Temporal multi-presence

A task can appear in multiple daily journals.

Example:

```text
2026-05-24: CREATED
2026-05-26: PROGRESSED
2026-05-28: COMPLETED
```

Each journal entry remains historically accurate.

---

## 2.2 Historical immutability

Completing a task today must not mutate past journals.

This is forbidden:

```text
Update previous journal entries to DONE
```

This is correct:

```text
Append a new COMPLETED event to today’s journal
```

---

## 2.3 Append-only task lifecycle

Task lifecycle state is represented by `task_event`.

The latest relevant event determines current status.

Example:

```text
Latest event = COMPLETED
Derived current status = completed
```

But older events remain unchanged.

---

## 2.4 Zero-query journal interface

The daily journal should be rendered directly from physical `task_event` rows for that day.

The app should not depend on virtual projections that erase the day’s historical context.

---

# 3. Sync Strategy Summary

The local-first sync system uses two different strategies:

```text
Mutable entities:
  - task
  - routine
  - alarm

Sync strategy:
  Field-level Last-Write-Wins using HLC

Append-only ledger entities:
  - task_event

Sync strategy:
  Idempotent append-only insert
```

This is the key final design decision.

Do **not** sync `task_event` as a mutable document unless you later explicitly support editing historical events.

---

# 4. Entity Overview

```text
routine
  Template generator for recurring tasks.

task
  Stable task identity.
  No status column.

task_event
  Immutable lifecycle and journal ledger.
  Source of truth for history and status derivation.

alarm
  Mutable time-based trigger.
  When fired, appends ALARM_FIRED event to task_event.
```

---

# 5. Field Group Design

## 5.1 Mutable tables use HLC field groups

Field-level HLC applies to:

```text
task
routine
alarm
```

It does not normally apply to:

```text
task_event
```

---

## 5.2 Task field groups

```text
title:
  - title

parent:
  - parent_id

routine:
  - routine_id

delete:
  - is_deleted
```

---

## 5.3 Routine field groups

```text
template:
  - template_title

schedule:
  - cron_schedule
  - timezone

active:
  - is_active

delete:
  - is_deleted
```

---

## 5.4 Alarm field groups

```text
trigger:
  - trigger_time

active:
  - is_active

delete:
  - is_deleted
```

---

# 6. SQLite / SQLDelight Schema

## 6.1 `routine`

```sql
CREATE TABLE routine (
    id TEXT PRIMARY KEY,

    template_title TEXT NOT NULL,
    cron_schedule TEXT NOT NULL,
    timezone TEXT NOT NULL,

    is_active INTEGER NOT NULL DEFAULT 1,
    is_deleted INTEGER NOT NULL DEFAULT 0,

    hlc_map TEXT NOT NULL DEFAULT '{}',
    dirty_fields TEXT NOT NULL DEFAULT '[]',
    is_dirty INTEGER NOT NULL DEFAULT 0,

    server_version INTEGER NOT NULL DEFAULT 0,

    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

---

## 6.2 `task`

```sql
CREATE TABLE task (
    id TEXT PRIMARY KEY,

    parent_id TEXT,
    routine_id TEXT,

    title TEXT NOT NULL,

    is_deleted INTEGER NOT NULL DEFAULT 0,

    hlc_map TEXT NOT NULL DEFAULT '{}',
    dirty_fields TEXT NOT NULL DEFAULT '[]',
    is_dirty INTEGER NOT NULL DEFAULT 0,

    server_version INTEGER NOT NULL DEFAULT 0,

    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,

    FOREIGN KEY(parent_id) REFERENCES task(id),
    FOREIGN KEY(routine_id) REFERENCES routine(id)
);
```

Important:

```text
No status column.
No completed_at column on task.
No current_state column on task.
```

Status is derived from `task_event`.

---

## 6.3 `task_event`

```sql
CREATE TABLE task_event (
    id TEXT PRIMARY KEY,

    task_id TEXT NOT NULL,

    journal_date TEXT NOT NULL,
    event_type TEXT NOT NULL,
    content TEXT,

    event_time INTEGER NOT NULL,

    is_deleted INTEGER NOT NULL DEFAULT 0,

    server_version INTEGER NOT NULL DEFAULT 0,

    is_dirty INTEGER NOT NULL DEFAULT 0,

    created_at INTEGER NOT NULL,

    FOREIGN KEY(task_id) REFERENCES task(id)
);
```

Important:

```text
task_event is append-only.
Do not update task_event during normal use.
Do not use hlc_map unless historical event editing is introduced later.
```

---

## 6.4 `alarm`

```sql
CREATE TABLE alarm (
    id TEXT PRIMARY KEY,

    task_id TEXT NOT NULL,

    trigger_time INTEGER NOT NULL,

    is_active INTEGER NOT NULL DEFAULT 1,
    is_deleted INTEGER NOT NULL DEFAULT 0,

    hlc_map TEXT NOT NULL DEFAULT '{}',
    dirty_fields TEXT NOT NULL DEFAULT '[]',
    is_dirty INTEGER NOT NULL DEFAULT 0,

    server_version INTEGER NOT NULL DEFAULT 0,

    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,

    FOREIGN KEY(task_id) REFERENCES task(id)
);
```

---

## 6.5 `sync_state`

```sql
CREATE TABLE sync_state (
    scope TEXT PRIMARY KEY,
    last_server_version INTEGER NOT NULL DEFAULT 0
);
```

Required scopes:

```text
routines
tasks
alarms
task_events
```

---

# 7. PostgreSQL Schema

PostgreSQL should mirror the same model, with server-side sync metadata.

## 7.1 Global server version sequence

```sql
CREATE SEQUENCE sync_server_version_seq;
```

---

## 7.2 Sync metadata trigger

```sql
CREATE OR REPLACE FUNCTION assign_sync_metadata()
RETURNS trigger AS $$
BEGIN
  NEW.server_version = nextval('sync_server_version_seq');
  NEW.server_updated_at = now();

  IF NEW.created_at IS NULL THEN
    NEW.created_at = now();
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

---

## 7.3 `routine`

```sql
CREATE TABLE routine (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,

    template_title text NOT NULL,
    cron_schedule text NOT NULL,
    timezone text NOT NULL,

    is_active boolean NOT NULL DEFAULT true,
    is_deleted boolean NOT NULL DEFAULT false,

    hlc_map jsonb NOT NULL DEFAULT '{}'::jsonb,

    server_version bigint NOT NULL,
    server_updated_at timestamptz NOT NULL DEFAULT now(),

    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_routine_user_server_version
ON routine(user_id, server_version);

CREATE TRIGGER trg_routine_sync_metadata
BEFORE INSERT OR UPDATE ON routine
FOR EACH ROW
EXECUTE FUNCTION assign_sync_metadata();
```

---

## 7.4 `task`

```sql
CREATE TABLE task (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,

    parent_id uuid,
    routine_id uuid,

    title text NOT NULL,

    is_deleted boolean NOT NULL DEFAULT false,

    hlc_map jsonb NOT NULL DEFAULT '{}'::jsonb,

    server_version bigint NOT NULL,
    server_updated_at timestamptz NOT NULL DEFAULT now(),

    created_at timestamptz NOT NULL DEFAULT now(),

    FOREIGN KEY(parent_id) REFERENCES task(id),
    FOREIGN KEY(routine_id) REFERENCES routine(id)
);

CREATE INDEX idx_task_user_server_version
ON task(user_id, server_version);

CREATE INDEX idx_task_parent
ON task(parent_id);

CREATE TRIGGER trg_task_sync_metadata
BEFORE INSERT OR UPDATE ON task
FOR EACH ROW
EXECUTE FUNCTION assign_sync_metadata();
```

---

## 7.5 `task_event`

```sql
CREATE TABLE task_event (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,

    task_id uuid NOT NULL,

    journal_date date NOT NULL,
    event_type text NOT NULL,
    content text,

    event_time timestamptz NOT NULL,

    is_deleted boolean NOT NULL DEFAULT false,

    server_version bigint NOT NULL,
    server_updated_at timestamptz NOT NULL DEFAULT now(),

    created_at timestamptz NOT NULL DEFAULT now(),

    FOREIGN KEY(task_id) REFERENCES task(id)
);

CREATE INDEX idx_task_event_user_server_version
ON task_event(user_id, server_version);

CREATE INDEX idx_task_event_user_journal_date
ON task_event(user_id, journal_date);

CREATE INDEX idx_task_event_task_id
ON task_event(task_id);

CREATE TRIGGER trg_task_event_sync_metadata
BEFORE INSERT OR UPDATE ON task_event
FOR EACH ROW
EXECUTE FUNCTION assign_sync_metadata();
```

---

## 7.6 `alarm`

```sql
CREATE TABLE alarm (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,

    task_id uuid NOT NULL,

    trigger_time timestamptz NOT NULL,

    is_active boolean NOT NULL DEFAULT true,
    is_deleted boolean NOT NULL DEFAULT false,

    hlc_map jsonb NOT NULL DEFAULT '{}'::jsonb,

    server_version bigint NOT NULL,
    server_updated_at timestamptz NOT NULL DEFAULT now(),

    created_at timestamptz NOT NULL DEFAULT now(),

    FOREIGN KEY(task_id) REFERENCES task(id)
);

CREATE INDEX idx_alarm_user_server_version
ON alarm(user_id, server_version);

CREATE INDEX idx_alarm_task_id
ON alarm(task_id);

CREATE TRIGGER trg_alarm_sync_metadata
BEFORE INSERT OR UPDATE ON alarm
FOR EACH ROW
EXECUTE FUNCTION assign_sync_metadata();
```

---

# 8. Deterministic IDs

Deterministic IDs are required for system-generated records.

This prevents duplicates when multiple offline devices perform the same generation.

---

## 8.1 Routine-generated task ID

For routine instances:

```text
task.id = UUIDv5("routine-task:" + routine_id + ":" + journal_date)
```

Example:

```text
routine_id = R-1
journal_date = 2026-05-24

task.id = UUIDv5("routine-task:R-1:2026-05-24")
```

---

## 8.2 Routine-created event ID

```text
task_event.id = UUIDv5("routine-created-event:" + routine_id + ":" + journal_date)
```

---

## 8.3 Alarm-fired event ID

```text
task_event.id = UUIDv5("alarm-fired:" + alarm_id + ":" + trigger_time)
```

This makes duplicate alarm firing idempotent.

---

## 8.4 Manual user-created records

Manual records may use random UUIDs:

```text
manual task id = random UUID
manual event id = random UUID
manual alarm id = random UUID
manual routine id = random UUID
```

---

# 9. Local Write Rules

## 9.1 Create task

Transaction:

```text
1. Insert task.
2. Insert CREATED task_event.
3. Mark both rows dirty.
```

The `task` row receives HLC tokens for populated mutable fields.

Example task `hlc_map`:

```json
{
  "title": "1715959378000-0000-deviceA"
}
```

---

## 9.2 Progress task

Do not mutate `task`.

Insert a new event:

```text
event_type = PROGRESSED
journal_date = today
content = optional note
```

---

## 9.3 Complete task

Do not mutate `task`.

Insert a new event:

```text
event_type = COMPLETED
journal_date = today
content = optional note
```

---

## 9.4 Create subtask

Transaction:

```text
1. Insert child task with parent_id.
2. Insert CREATED task_event for child task.
3. Mark both rows dirty.
```

Parent task history remains untouched.

---

## 9.5 Execute routine

Transaction:

```text
1. Generate deterministic task ID.
2. Generate deterministic CREATED event ID.
3. Insert task if not exists.
4. Insert CREATED task_event if not exists.
5. Mark inserted rows dirty.
```

---

## 9.6 Fire alarm

Transaction:

```text
1. Set alarm.is_active = false.
2. Update alarm.hlc_map.active.
3. Add "active" to alarm.dirty_fields.
4. Set alarm.is_dirty = true.
5. Generate deterministic ALARM_FIRED task_event ID.
6. Insert ALARM_FIRED event if not exists.
7. Mark event dirty.
```

---

# 10. Derived Status Rules

The `task` table has no status.

Current status is derived from latest task event.

Example logic:

```sql
SELECT event_type
FROM task_event
WHERE task_id = ?
  AND is_deleted = 0
ORDER BY event_time DESC, created_at DESC
LIMIT 1;
```

Suggested derived status mapping:

```text
No event:
  unknown

Latest CREATED:
  pending

Latest MIGRATED:
  pending

Latest PROGRESSED:
  in_progress

Latest ALARM_FIRED:
  pending

Latest COMPLETED:
  completed

Latest CANCELLED:
  cancelled
```

Parent task status may be derived from child tasks:

```text
All children completed:
  parent derived status = completed

Some children progressed/completed:
  parent derived status = in_progress

No child progress:
  parent derived status = pending
```

Do not write this derived status back into `task`.

---

# 11. Daily Journal Query

Render a journal by reading physical events for that date.

```sql
SELECT
    t.id AS task_id,
    t.title,
    e.id AS event_id,
    e.event_type,
    e.content,
    e.event_time
FROM task_event e
JOIN task t ON e.task_id = t.id
WHERE e.journal_date = ?
  AND e.is_deleted = 0
  AND t.is_deleted = 0
ORDER BY e.event_time ASC, e.created_at ASC;
```

This preserves the “what actually happened that day” model.

---

# 12. Sync API Design

Use batch sync APIs for the KMP app.

```text
/sync/routines
/sync/tasks
/sync/alarms
/sync/task-events
```

---

## 12.1 Required sync order

Because tables have dependencies, sync in this order:

Pull:

```text
1. routines
2. tasks
3. alarms
4. task_events
```

Push:

```text
1. routines
2. tasks
3. alarms
4. task_events
```

This prevents foreign-key issues.

---

## 12.2 Pull API

Example:

```http
GET /sync/tasks?cursor=123&limit=500
Authorization: Bearer <token>
```

Response:

```json
{
  "items": [],
  "next_cursor": 456,
  "has_more": false
}
```

Server query pattern:

```sql
SELECT *
FROM task
WHERE user_id = $1
  AND server_version > $2
ORDER BY server_version ASC
LIMIT $3;
```

Rules:

```text
Use server_version as cursor.
Return soft-deleted rows.
Never use client timestamp as sync cursor.
```

---

## 12.3 Push API for mutable resources

Applies to:

```text
routine
task
alarm
```

Example:

```http
POST /sync/tasks
Authorization: Bearer <token>
Content-Type: application/json
```

Request:

```json
{
  "items": [
    {
      "id": "5fd1fa4d-7c2b-4e5a-aef0-4f60ef0c4001",
      "parent_id": null,
      "routine_id": null,
      "title": "Setup KMP project",
      "is_deleted": false,
      "hlc_map": {
        "title": "1715959378000-0000-deviceA"
      },
      "dirty_fields": ["title"]
    }
  ]
}
```

Response:

```json
{
  "items": [
    {
      "id": "5fd1fa4d-7c2b-4e5a-aef0-4f60ef0c4001",
      "parent_id": null,
      "routine_id": null,
      "title": "Setup KMP project",
      "is_deleted": false,
      "hlc_map": {
        "title": "1715959378000-0000-deviceA"
      },
      "accepted_fields": ["title"],
      "rejected_fields": [],
      "server_version": 125,
      "server_updated_at": "2026-05-24T10:01:00Z"
    }
  ]
}
```

---

## 12.4 Push API for `task_event`

`task_event` uses append-only sync.

Example:

```http
POST /sync/task-events
Authorization: Bearer <token>
Content-Type: application/json
```

Request:

```json
{
  "items": [
    {
      "id": "f1111111-2222-3333-4444-555555555555",
      "task_id": "5fd1fa4d-7c2b-4e5a-aef0-4f60ef0c4001",
      "journal_date": "2026-05-24",
      "event_type": "CREATED",
      "content": null,
      "event_time": "2026-05-24T10:00:00Z",
      "is_deleted": false
    }
  ]
}
```

Server behavior:

```text
If event ID does not exist:
  insert event.

If event ID already exists for the same user:
  return existing canonical event.

Do not merge event content.
Do not overwrite historical events.
```

Response:

```json
{
  "items": [
    {
      "id": "f1111111-2222-3333-4444-555555555555",
      "task_id": "5fd1fa4d-7c2b-4e5a-aef0-4f60ef0c4001",
      "journal_date": "2026-05-24",
      "event_type": "CREATED",
      "content": null,
      "event_time": "2026-05-24T10:00:00Z",
      "is_deleted": false,
      "accepted": true,
      "server_version": 126,
      "server_updated_at": "2026-05-24T10:01:00Z"
    }
  ]
}
```

---

# 13. Server Push Algorithms

## 13.1 Mutable resource push

Applies to:

```text
task
routine
alarm
```

Algorithm:

```text
For each pushed item:

1. Authenticate user.
2. Validate payload.
3. Open DB transaction.
4. SELECT existing row FOR UPDATE by id and user_id.
5. If row does not exist:
     insert row using authenticated user_id.
     accepted_fields = dirty_fields.
     rejected_fields = [].
6. If row exists:
     for each dirty field:
       compare incoming HLC with existing HLC.
       if incoming HLC is greater:
           update that field group.
           update hlc_map for that field.
           add to accepted_fields.
       else:
           keep existing field group.
           add to rejected_fields.
7. Commit.
8. Return canonical row.
```

Row lock:

```sql
SELECT *
FROM task
WHERE id = $1
  AND user_id = $2
FOR UPDATE;
```

---

## 13.2 Append-only event push

Applies to:

```text
task_event
```

Algorithm:

```text
For each pushed event:

1. Authenticate user.
2. Validate payload.
3. Verify task_id belongs to current user.
4. Open DB transaction.
5. Check whether task_event.id already exists for user.
6. If not exists:
     insert event.
7. If exists:
     do not update existing event.
8. Commit.
9. Return canonical event.
```

This makes event push idempotent.

---

# 14. Client Sync Engine

## 14.1 Main sync

```text
syncAll():
  acquire global sync mutex

  try:
    pull routines
    pull tasks
    pull alarms
    pull task_events

    push routines
    push tasks
    push alarms
    push task_events
  finally:
    release global sync mutex
```

---

## 14.2 Pull flow

```text
pull(scope):
  cursor = sync_state[scope].last_server_version

  do:
    response = GET /sync/{scope}?cursor=cursor&limit=500

    begin SQLite transaction

    for remote_row in response.items:
      merge remote_row locally
      cursor = max(cursor, remote_row.server_version)

    update sync_state[scope].last_server_version = cursor

    commit SQLite transaction

  while response.has_more
```

---

## 14.3 Push flow

```text
push(scope):
  dirty_rows = SELECT rows WHERE is_dirty = 1

  split dirty_rows into batches

  for each batch:
    response = POST /sync/{scope}

    begin SQLite transaction

    for returned_row in response.items:
      merge returned row locally
      clear accepted dirty fields

    commit SQLite transaction
```

For `task_event`:

```text
If server confirms event:
  set is_dirty = 0
  update server_version
```

---

# 15. Business APIs

Business APIs are separate from sync APIs.

They are used by non-local-first clients, web dashboard, backend jobs, admin tools, and integrations.

Example APIs:

```http
POST   /api/tasks
PATCH  /api/tasks/{id}
DELETE /api/tasks/{id}

POST   /api/tasks/{id}/events
GET    /api/journals/{date}

POST   /api/routines
PATCH  /api/routines/{id}
DELETE /api/routines/{id}

POST   /api/alarms
PATCH  /api/alarms/{id}
DELETE /api/alarms/{id}
```

Rules:

```text
Business APIs must also update sync metadata.
Business APIs must generate server-side HLC for mutable resources.
Business APIs must use soft deletes.
Business APIs must never write task.status.
```

For the KMP local-first app:

```text
UI writes SQLite first.
SyncEngine uses /sync APIs.
UI does not call business mutation APIs for ordinary local-first writes.
```

---

# 16. Kotlin DTOs

## 16.1 HLC token

```kotlin
@JvmInline
value class HlcToken(val value: String) : Comparable<HlcToken> {
    override fun compareTo(other: HlcToken): Int {
        return value.compareTo(other.value)
    }
}
```

---

## 16.2 Field groups

```kotlin
enum class TaskFieldGroup(val wireName: String) {
    TITLE("title"),
    PARENT("parent"),
    ROUTINE("routine"),
    DELETE("delete")
}

enum class RoutineFieldGroup(val wireName: String) {
    TEMPLATE("template"),
    SCHEDULE("schedule"),
    ACTIVE("active"),
    DELETE("delete")
}

enum class AlarmFieldGroup(val wireName: String) {
    TRIGGER("trigger"),
    ACTIVE("active"),
    DELETE("delete")
}
```

---

## 16.3 Generic pull response

```kotlin
@Serializable
data class SyncPullResponse<T>(
    val items: List<T>,

    @SerialName("next_cursor")
    val nextCursor: Long,

    @SerialName("has_more")
    val hasMore: Boolean
)
```

---

## 16.4 Generic push request

```kotlin
@Serializable
data class SyncPushRequest<T>(
    val items: List<T>
)
```

---

## 16.5 Task push DTO

```kotlin
@Serializable
data class TaskPushDto(
    val id: String,

    @SerialName("parent_id")
    val parentId: String? = null,

    @SerialName("routine_id")
    val routineId: String? = null,

    val title: String,

    @SerialName("is_deleted")
    val isDeleted: Boolean = false,

    @SerialName("hlc_map")
    val hlcMap: Map<String, String>,

    @SerialName("dirty_fields")
    val dirtyFields: Set<String>
)
```

---

## 16.6 Task event push DTO

```kotlin
@Serializable
data class TaskEventPushDto(
    val id: String,

    @SerialName("task_id")
    val taskId: String,

    @SerialName("journal_date")
    val journalDate: String,

    @SerialName("event_type")
    val eventType: String,

    val content: String? = null,

    @SerialName("event_time")
    val eventTime: String,

    @SerialName("is_deleted")
    val isDeleted: Boolean = false
)
```

---

## 16.7 Mutable push result DTO

```kotlin
@Serializable
data class MutablePushResultDto<T>(
    val item: T,

    @SerialName("accepted_fields")
    val acceptedFields: Set<String>,

    @SerialName("rejected_fields")
    val rejectedFields: Set<String>,

    @SerialName("server_version")
    val serverVersion: Long,

    @SerialName("server_updated_at")
    val serverUpdatedAt: String
)
```

---

## 16.8 Task event push result DTO

```kotlin
@Serializable
data class TaskEventPushResultDto(
    val id: String,

    @SerialName("task_id")
    val taskId: String,

    @SerialName("journal_date")
    val journalDate: String,

    @SerialName("event_type")
    val eventType: String,

    val content: String? = null,

    @SerialName("event_time")
    val eventTime: String,

    @SerialName("is_deleted")
    val isDeleted: Boolean,

    val accepted: Boolean,

    @SerialName("server_version")
    val serverVersion: Long,

    @SerialName("server_updated_at")
    val serverUpdatedAt: String
)
```

---

# 17. Required Validation Rules

The API server must validate:

```text
- authenticated user exists
- ID is valid UUID
- user cannot provide or override user_id
- user cannot provide or override server_version
- user cannot provide or override server_updated_at
- parent_id belongs to same user
- routine_id belongs to same user
- task_id belongs to same user
- dirty_fields is non-empty for mutable push
- every dirty field exists in hlc_map
- every dirty field is valid for the resource
- event_type is valid
- journal_date is valid date
- event_time is valid timestamp
```

---

# 18. Required Invariants

The implementation must preserve these invariants:

```text
1. task has no status column.
2. task_event is the source of lifecycle truth.
3. task_event is append-only during normal use.
4. Daily journals are rendered from task_event.
5. Mutable tables use field-level HLC.
6. task_event uses idempotent append-only sync.
7. Deletes are soft deletes.
8. Routine-generated tasks use deterministic IDs.
9. Alarm-fired events use deterministic IDs.
10. Sync always pulls before pushing.
11. Sync order respects dependencies.
12. server_version is generated only by server.
13. user_id is derived only from authenticated session.
14. UI writes to SQLite first.
15. SyncEngine uses batch /sync APIs.
16. Business APIs are separate from sync APIs.
17. Realtime notifications only trigger pull/sync.
```

---

# 19. Implementation Priority

Build in this order:

```text
1. SQLite schema.
2. PostgreSQL schema.
3. HLC generator and comparator.
4. Deterministic UUID generator.
5. Local dirty tracking for task, routine, alarm.
6. Append-only local task_event creation.
7. Local derived status query.
8. Local daily journal query.
9. Pull APIs.
10. Client pull merge.
11. Push APIs for mutable resources.
12. Push API for task_event.
13. Client push confirmation merge.
14. Routine generator.
15. Alarm firing flow.
16. Business APIs.
17. Realtime notification trigger.
18. Tests for offline duplicate routine/alarm cases.
```

---

# 20. Final Verdict

This design works well with the sync system.

The final architecture should be:

```text
task:
  mutable identity entity
  synced with field-level HLC

routine:
  mutable generator entity
  synced with field-level HLC

alarm:
  mutable trigger entity
  synced with field-level HLC

task_event:
  immutable ledger entity
  synced with idempotent append-only insert
```

The most important design principle is:

```text
Do not force the ledger into a mutable-document sync model.
```

Your product is event-sourced. The sync design should respect that by making `task_event` append-only and using HLC only where mutation actually exists.

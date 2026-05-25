# Final Design Spec: Local-First Sync Between SQLite, API Server, and PostgreSQL

## 0. Purpose

Build a clean, robust, stable local-first synchronization system for a Kotlin Multiplatform app.

The system consists of:

```text
Client:
  Kotlin Multiplatform app
  SQLite / SQLDelight local database

Server:
  API server layer
  PostgreSQL database
```

The design supports:

```text
- Offline-first local writes
- Deterministic sync
- Field-level conflict resolution
- Soft deletes
- Batch sync APIs
- Single-resource business APIs
- Server-side ownership and validation
```

---

# 1. Core Sync Model

## 1.1 Conflict model

Use:

```text
Field-level Last-Write-Wins
```

Do not use row-level Last-Write-Wins.

Each logical field group has its own Hybrid Logical Clock token.

Example:

```json
{
  "money": "1715959378000-0001-deviceA",
  "note": "1715959381000-0000-deviceA",
  "category": "1715959390000-0000-deviceB",
  "delete": "1715959400000-0000-deviceA"
}
```

Conflict rule:

```text
If incoming field HLC > existing field HLC:
    accept incoming field group
else:
    keep existing field group
```

---

## 1.2 Clock model

Use Hybrid Logical Clock, abbreviated as HLC.

Canonical token format:

```text
<physical_ms>-<logical_counter>-<node_id>
```

Example:

```text
1715959378000-0001-deviceA
```

Rules:

```text
physical_ms:
  - Unix epoch milliseconds
  - fixed 13 digits

logical_counter:
  - zero-padded
  - at least 4 digits

node_id:
  - stable per local installation or server node
  - never empty
```

HLC tokens must be lexicographically comparable.

Example:

```text
1715959378000-0001-deviceA
>
1715959378000-0000-deviceB
```

---

## 1.3 Sync protocol

Every sync cycle must follow:

```text
1. Pull server changes.
2. Merge server changes into local SQLite.
3. Push local dirty changes.
4. Merge server-confirmed rows into local SQLite.
5. Clear accepted dirty fields.
```

Never push before pulling.

---

## 1.4 Realtime strategy

Realtime subscriptions, WebSockets, or push notifications must be used only as a notification trigger.

They may say:

```text
Something changed. Please pull.
```

They must not be used as the source-of-truth data stream.

The local database should only be updated through the normal pull/merge process.

Current implementation status:

```text
- Automatic sync on app startup: implemented
- Automatic sync after local writes: implemented
- Remote-change notification trigger: not yet implemented
```

---

## 1.5 Client sync trigger policy

The client should not require a manual "Sync Now" action for normal operation.

Normal trigger rules:

```text
1. On app startup, schedule an immediate sync cycle.
2. After each local-first write to SQLite, schedule a sync cycle.
3. Multiple rapid local writes may be coalesced with a short debounce.
4. If a sync is already running and another trigger arrives, queue one follow-up sync cycle.
5. A manual sync action may still exist as a fallback or debugging affordance.
```

Notes:

```text
- Debounce length is an implementation detail, not a protocol-level invariant.
- Trigger timing may vary by platform lifecycle constraints.
- These trigger rules do not change the required sync protocol order.
```

---

# 2. API Families

The system must expose two separate API families.

---

## 2.1 Sync API

Used by the KMP local-first app’s SyncEngine.

```text
/sync/{resource}
```

Example:

```http
GET  /sync/transactions?cursor=123&limit=500
POST /sync/transactions
```

Sync APIs are:

```text
- batch-oriented
- used by local-first clients
- responsible for pull/push sync
- include hlc_map
- include dirty_fields on push
- return accepted_fields and rejected_fields on push
```

The mobile app normally uses this flow:

```text
UI action
  -> write to SQLite
  -> mark dirty field groups
  -> schedule sync
  -> SyncEngine later calls /sync APIs
```

The mobile app should not normally call remote mutation business APIs directly.

---

## 2.2 Business API

Used by:

```text
- web dashboard
- admin tools
- backend jobs
- support tools
- non-local-first clients
- third-party integrations
```

Example:

```http
GET    /api/transactions
GET    /api/transactions/{id}
POST   /api/transactions
PATCH  /api/transactions/{id}
DELETE /api/transactions/{id}
```

Business APIs are:

```text
- single-resource or paginated APIs
- not used by the local-first mobile UI for ordinary writes
- responsible for normal online CRUD
- required to generate server-side HLC tokens
- required to update server_version
- required to use soft deletes
```

---

# 3. Field Groups

Do not create one HLC per physical database column blindly.

Create HLCs per semantic field group.

Example resource: `transactions`.

```text
money:
  - amount
  - currency

note:
  - note

category:
  - category_id

delete:
  - is_deleted
```

This prevents invalid merged states such as:

```text
amount from device A
currency from device B
```

---

# 4. PostgreSQL Design

## 4.1 Required columns for every synced table

Every synced PostgreSQL table must include:

```sql
id uuid primary key,
user_id uuid not null,

is_deleted boolean not null default false,

hlc_map jsonb not null default '{}'::jsonb,

server_version bigint not null,
server_updated_at timestamptz not null default now(),

created_at timestamptz not null default now()
```

---

## 4.2 Example table: transactions

```sql
create table transactions (
  id uuid primary key,
  user_id uuid not null,

  amount numeric(12,2),
  currency text,
  note text,
  category_id uuid,

  is_deleted boolean not null default false,

  hlc_map jsonb not null default '{}'::jsonb,

  server_version bigint not null,
  server_updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);
```

---

## 4.3 Server version sequence

Use a server-generated monotonic sync cursor.

```sql
create sequence sync_server_version_seq;
```

Every insert or update must assign:

```text
server_version = nextval('sync_server_version_seq')
server_updated_at = now()
```

Trigger:

```sql
create or replace function assign_sync_metadata()
returns trigger as $$
begin
  new.server_version = nextval('sync_server_version_seq');
  new.server_updated_at = now();

  if new.created_at is null then
    new.created_at = now();
  end if;

  return new;
end;
$$ language plpgsql;
```

Apply trigger:

```sql
create trigger trg_transactions_sync_metadata
before insert or update on transactions
for each row
execute function assign_sync_metadata();
```

---

## 4.4 Required indexes

```sql
create index idx_transactions_user_server_version
on transactions(user_id, server_version);
```

Optional:

```sql
create index idx_transactions_user_not_deleted
on transactions(user_id, is_deleted);
```

---

## 4.5 Server ownership rule

The client must never control:

```text
user_id
server_version
server_updated_at
created_at
```

The server must derive `user_id` from the authenticated session.

---

# 5. SQLite / SQLDelight Design

## 5.1 Required local columns for every synced table

Every local synced table must include:

```sql
id text primary key,

is_deleted integer not null default 0,

hlc_map text not null default '{}',

dirty_fields text not null default '[]',
is_dirty integer not null default 0,

last_server_version integer not null default 0,

created_at integer not null,
updated_at integer not null
```

---

## 5.2 Example local table: transactions

```sql
create table transactions (
  id text primary key,

  amount real,
  currency text,
  note text,
  category_id text,

  is_deleted integer not null default 0,

  hlc_map text not null default '{}',

  dirty_fields text not null default '[]',
  is_dirty integer not null default 0,

  last_server_version integer not null default 0,

  created_at integer not null,
  updated_at integer not null
);
```

---

## 5.3 Sync state table

```sql
create table sync_state (
  scope text primary key,
  last_server_version integer not null default 0
);
```

Example scopes:

```text
transactions
categories
accounts
```

---

# 6. Local Write Rules

All local-first writes must go to SQLite first.

## 6.1 Create

When creating a local transaction:

```text
1. Generate client UUID.
2. Generate HLC token for each populated field group.
3. Insert row into SQLite.
4. Set dirty_fields to the changed field groups.
5. Set is_dirty = true.
```

Example dirty fields:

```json
["money", "note", "category"]
```

---

## 6.2 Update

When updating `amount` or `currency`:

```text
1. Update amount/currency locally.
2. Generate new HLC for "money".
3. Update hlc_map.money.
4. Add "money" to dirty_fields.
5. Set is_dirty = true.
```

When updating `note`:

```text
1. Update note locally.
2. Generate new HLC for "note".
3. Update hlc_map.note.
4. Add "note" to dirty_fields.
5. Set is_dirty = true.
```

---

## 6.3 Delete

Never hard-delete synced rows during normal app operation.

When deleting locally:

```text
1. Set is_deleted = true.
2. Generate new HLC for "delete".
3. Update hlc_map.delete.
4. Add "delete" to dirty_fields.
5. Set is_dirty = true.
```

---

# 7. Client Merge Rules

## 7.1 Pull merge

For each remote row:

```text
For each field group:
    remote_hlc = remote.hlc_map[field_group]
    local_hlc = local.hlc_map[field_group]

    If remote_hlc > local_hlc:
        overwrite local field group with remote values
        update local hlc_map[field_group]
    Else:
        keep local values
```

After merging the row:

```text
last_server_version = remote.server_version
```

Important:

```text
Do not clear dirty_fields during pull.
Dirty fields are cleared only after push confirmation.
```

---

## 7.2 Push confirmation merge

After push, the server returns canonical rows.

For each returned row:

```text
1. Merge the canonical row using the same field-level HLC rules.
2. Clear only accepted_fields.
3. Keep rejected_fields dirty.
4. If dirty_fields becomes empty, set is_dirty = false.
```

---

# 8. Sync API Design

## 8.1 Pull endpoint

```http
GET /sync/transactions?cursor={last_server_version}&limit={limit}
Authorization: Bearer <access_token>
```

Example:

```http
GET /sync/transactions?cursor=123&limit=500
```

Response:

```json
{
  "items": [
    {
      "id": "5fd1fa4d-7c2b-4e5a-aef0-4f60ef0c4001",
      "amount": 12.5,
      "currency": "USD",
      "note": "Lunch",
      "category_id": "1a1a1a1a-1111-2222-3333-444444444444",
      "is_deleted": false,
      "hlc_map": {
        "money": "1715959378000-0001-deviceA",
        "note": "1715959381000-0000-deviceA",
        "category": "1715959390000-0000-deviceA"
      },
      "server_version": 124,
      "server_updated_at": "2026-05-24T10:00:00Z",
      "created_at": "2026-05-24T09:00:00Z"
    }
  ],
  "next_cursor": 124,
  "has_more": false
}
```

Server query:

```sql
select *
from transactions
where user_id = $1
  and server_version > $2
order by server_version asc
limit $3;
```

Rules:

```text
- Use server_version as cursor.
- Do not use client timestamp as sync cursor.
- Do not use local updated_at as sync cursor.
- Return soft-deleted rows too.
```

---

## 8.2 Push endpoint

```http
POST /sync/transactions
Authorization: Bearer <access_token>
Content-Type: application/json
```

Request:

```json
{
  "items": [
    {
      "id": "5fd1fa4d-7c2b-4e5a-aef0-4f60ef0c4001",
      "amount": 12.5,
      "currency": "USD",
      "note": "Lunch",
      "category_id": "1a1a1a1a-1111-2222-3333-444444444444",
      "is_deleted": false,
      "hlc_map": {
        "money": "1715959378000-0001-deviceA",
        "note": "1715959381000-0000-deviceA",
        "category": "1715959390000-0000-deviceA"
      },
      "dirty_fields": ["money", "note", "category"]
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
      "amount": 12.5,
      "currency": "USD",
      "note": "Lunch",
      "category_id": "1a1a1a1a-1111-2222-3333-444444444444",
      "is_deleted": false,
      "hlc_map": {
        "money": "1715959378000-0001-deviceA",
        "note": "1715959381000-0000-deviceA",
        "category": "1715959390000-0000-deviceA"
      },
      "accepted_fields": ["money", "note", "category"],
      "rejected_fields": [],
      "server_version": 125,
      "server_updated_at": "2026-05-24T10:01:00Z"
    }
  ]
}
```

---

# 9. Server Push Algorithm

The main push merge logic belongs in the application layer, not in a PostgreSQL RPC.

The API server must own:

```text
- authentication
- authorization
- payload validation
- HLC comparison
- business validation
- canonical response generation
- observability
- sync diagnostics
```

PostgreSQL must own:

```text
- transactions
- row locking
- constraints
- persistence
- server_version generation
```

---

## 9.1 Push processing algorithm

For each pushed item:

```text
1. Authenticate user.
2. Validate payload.
3. Open database transaction.
4. Select existing row by id and user_id FOR UPDATE.
5. If row does not exist:
      insert new row using authenticated user_id.
      accepted_fields = dirty_fields.
      rejected_fields = [].
6. If row exists:
      for each dirty field group:
          compare incoming HLC with existing HLC.
          if incoming HLC is greater:
              update that field group.
              update that HLC key.
              add field group to accepted_fields.
          else:
              keep existing value.
              add field group to rejected_fields.
7. Commit transaction.
8. Return canonical row with accepted_fields and rejected_fields.
```

SQL locking pattern:

```sql
select *
from transactions
where id = $1
  and user_id = $2
for update;
```

All comparison and update logic must run inside the same database transaction.

---

# 10. Business API Design

Business APIs are separate from sync APIs.

They are useful for server-side tools, web dashboards, admin panels, and integrations.

---

## 10.1 Create transaction

```http
POST /api/transactions
Authorization: Bearer <access_token>
Content-Type: application/json
```

Request:

```json
{
  "amount": 12.5,
  "currency": "USD",
  "note": "Lunch",
  "category_id": "1a1a1a1a-1111-2222-3333-444444444444"
}
```

Server behavior:

```text
1. Authenticate user.
2. Validate request.
3. Generate transaction ID if not supplied.
4. Generate server-side HLC tokens for populated field groups.
5. Insert row.
6. Return canonical row.
```

Example server-generated HLC map:

```json
{
  "money": "1715959378000-0000-server",
  "note": "1715959378000-0000-server",
  "category": "1715959378000-0000-server"
}
```

---

## 10.2 Read one transaction

```http
GET /api/transactions/{id}
Authorization: Bearer <access_token>
```

Response:

```json
{
  "id": "5fd1fa4d-7c2b-4e5a-aef0-4f60ef0c4001",
  "amount": 12.5,
  "currency": "USD",
  "note": "Lunch",
  "category_id": "1a1a1a1a-1111-2222-3333-444444444444",
  "is_deleted": false,
  "server_version": 101,
  "server_updated_at": "2026-05-24T10:00:00Z",
  "created_at": "2026-05-24T10:00:00Z"
}
```

---

## 10.3 List transactions

```http
GET /api/transactions?limit=50&cursor=...
Authorization: Bearer <access_token>
```

This is ordinary business pagination.

Do not use this as the local-first sync cursor.

---

## 10.4 Patch transaction

```http
PATCH /api/transactions/{id}
Authorization: Bearer <access_token>
Content-Type: application/json
```

Request:

```json
{
  "note": "Updated lunch note"
}
```

Server behavior:

```text
1. Authenticate user.
2. Validate ownership.
3. Validate request.
4. Update note.
5. Generate new server-side HLC for field group "note".
6. Update hlc_map.note.
7. Increment server_version through DB trigger.
8. Return canonical row.
```

---

## 10.5 Delete transaction

```http
DELETE /api/transactions/{id}
Authorization: Bearer <access_token>
```

Server behavior:

```text
1. Authenticate user.
2. Validate ownership.
3. Soft delete only.
4. Set is_deleted = true.
5. Generate new server-side HLC for field group "delete".
6. Update hlc_map.delete.
7. Increment server_version through DB trigger.
8. Return success or canonical row.
```

Do not hard-delete.

---

# 11. Kotlin / KMP Type Definitions

Use Kotlin types as the canonical app-side contract.

---

## 11.1 HLC token

```kotlin
@JvmInline
value class HlcToken(val value: String) : Comparable<HlcToken> {
    override fun compareTo(other: HlcToken): Int {
        return value.compareTo(other.value)
    }
}
```

---

## 11.2 Transaction field groups

```kotlin
enum class TransactionFieldGroup(val wireName: String) {
    MONEY("money"),
    NOTE("note"),
    CATEGORY("category"),
    DELETE("delete")
}
```

---

## 11.3 Local transaction entity

```kotlin
data class LocalTransactionEntity(
    val id: String,

    val amount: Double?,
    val currency: String?,
    val note: String?,
    val categoryId: String?,

    val isDeleted: Boolean,

    val hlcMap: Map<String, String>,
    val dirtyFields: Set<String>,
    val isDirty: Boolean,

    val lastServerVersion: Long,

    val createdAt: Long,
    val updatedAt: Long
)
```

---

## 11.4 Pull response DTO

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

## 11.5 Transaction sync DTO

```kotlin
@Serializable
data class TransactionSyncDto(
    val id: String,

    val amount: Double? = null,
    val currency: String? = null,
    val note: String? = null,

    @SerialName("category_id")
    val categoryId: String? = null,

    @SerialName("is_deleted")
    val isDeleted: Boolean = false,

    @SerialName("hlc_map")
    val hlcMap: Map<String, String>,

    @SerialName("server_version")
    val serverVersion: Long,

    @SerialName("server_updated_at")
    val serverUpdatedAt: String,

    @SerialName("created_at")
    val createdAt: String
)
```

---

## 11.6 Push request DTO

```kotlin
@Serializable
data class SyncPushRequest<T>(
    val items: List<T>
)
```

```kotlin
@Serializable
data class TransactionPushDto(
    val id: String,

    val amount: Double? = null,
    val currency: String? = null,
    val note: String? = null,

    @SerialName("category_id")
    val categoryId: String? = null,

    @SerialName("is_deleted")
    val isDeleted: Boolean = false,

    @SerialName("hlc_map")
    val hlcMap: Map<String, String>,

    @SerialName("dirty_fields")
    val dirtyFields: Set<String>
)
```

---

## 11.7 Push response DTO

```kotlin
@Serializable
data class SyncPushResponse<T>(
    val items: List<T>
)
```

```kotlin
@Serializable
data class TransactionPushResultDto(
    val id: String,

    val amount: Double? = null,
    val currency: String? = null,
    val note: String? = null,

    @SerialName("category_id")
    val categoryId: String? = null,

    @SerialName("is_deleted")
    val isDeleted: Boolean = false,

    @SerialName("hlc_map")
    val hlcMap: Map<String, String>,

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

# 12. Sync Engine Design

## 12.1 Main sync function

```text
sync(scope):
    acquire sync mutex

    try:
        pull(scope)
        push(scope)
    finally:
        release sync mutex
```

Only one sync operation per scope may run at a time.

---

## 12.2 Pull algorithm

```text
pull(scope):
    cursor = sync_state[scope].last_server_version

    do:
        response = GET /sync/{scope}?cursor=cursor&limit=500

        begin local SQLite transaction

        for remote_row in response.items:
            merge remote_row into local table
            cursor = max(cursor, remote_row.server_version)

        update sync_state[scope].last_server_version = cursor

        commit local SQLite transaction

    while response.has_more
```

Rules:

```text
- Commit each pull page transactionally.
- Advance cursor only after the page has been merged successfully.
- Include soft-deleted rows.
```

---

## 12.3 Push algorithm

```text
push(scope):
    dirty_rows = select rows where is_dirty = true

    split dirty_rows into batches

    for each batch:
        response = POST /sync/{scope}

        begin local SQLite transaction

        for canonical_row in response.items:
            merge canonical_row into local table
            clear accepted dirty fields
            keep rejected dirty fields

        commit local SQLite transaction
```

Rules:

```text
- Never clear dirty fields without server confirmation.
- Retry failed push later.
- Push must be idempotent.
```

---

# 13. Validation Rules

The API server must validate:

```text
- id is valid UUID
- hlc_map exists
- dirty_fields is not empty on push
- every dirty field exists in hlc_map
- every dirty field is known for the resource
- field values match expected types
- user cannot set user_id
- user cannot set server_version
- user cannot set server_updated_at
- user cannot mutate records owned by another user
- soft-deleted records cannot be updated except through allowed restore/delete semantics
```

Invalid request:

```http
400 Bad Request
```

Unauthenticated request:

```http
401 Unauthorized
```

Forbidden ownership violation:

```http
403 Forbidden
```

Missing resource:

```http
404 Not Found
```

---

# 14. Idempotency

Push must be idempotent.

The same push request sent multiple times must produce the same final business state.

This is guaranteed by:

```text
- stable client-generated IDs
- field-level HLC comparison
- deterministic merge rules
- server-side row locking
- transaction boundaries
```

---

# 15. Error Handling

## 15.1 Pull failure

If pull fails:

```text
- do not advance sync_state cursor
- keep local dirty state unchanged
- retry later
```

If the client pulls multiple pages:

```text
- each page may be committed independently
- cursor may advance only after successful page merge
```

---

## 15.2 Push failure

If push fails:

```text
- keep local dirty state unchanged
- retry later
```

Do not clear dirty fields.

---

## 15.3 Partial push success

If the server returns per-row results:

```text
- clear only accepted_fields
- keep rejected_fields dirty
- keep failed rows dirty
```

---

# 16. Sync Triggers

The client should trigger sync on:

```text
- app start
- app foreground
- network becomes online
- 3 to 5 seconds after user stops editing
- app background transition
- manual refresh
- realtime notification received
```

Do not push on every keystroke.

Realtime notification should only trigger pull or full sync.

---

# 17. Required Invariants

The implementation must preserve these invariants:

```text
1. Server version is generated only by the server.
2. User ID is derived only from authenticated session.
3. Client never controls ownership fields.
4. Sync always runs pull before push.
5. Dirty fields are cleared only after server confirmation.
6. Deletes are soft deletes.
7. Realtime events only trigger pull or sync.
8. Field groups are merged atomically.
9. Push is idempotent.
10. Server push merge runs inside a DB transaction.
11. Existing rows are locked with SELECT FOR UPDATE before merge.
12. HLC comparison is deterministic.
13. Local UI writes to SQLite first.
14. Sync API is batch-oriented.
15. Business API is separate from Sync API.
16. Business API changes must also update HLC tokens.
17. Pull cursor is server_version, never client timestamp.
```

---

# 18. What AI Coding Tools Must Not Do

```text
Do not use row-level Last-Write-Wins.
Do not use client timestamps as sync cursor.
Do not use local updated_at for remote delta sync.
Do not hard-delete synced rows.
Do not put main sync orchestration into PostgreSQL RPC.
Do not clear dirty state after network request unless server confirms it.
Do not trust user_id from client payload.
Do not apply realtime payloads directly to SQLite.
Do not sync on every keystroke.
Do not compare hlc_map JSON objects as a whole.
Do not overwrite entire rows during merge.
Do not let the mobile UI call business mutation APIs for ordinary local-first writes.
Do not use TypeScript as the canonical app-side type system.
```

---

# 19. Recommended Implementation Order

```text
1. Define field groups per resource.
2. Implement HLC generator and comparator in Kotlin.
3. Implement SQLite / SQLDelight schema.
4. Implement local dirty tracking.
5. Implement local field-level merge resolver.
6. Implement PostgreSQL schema.
7. Implement server_version sequence and triggers.
8. Implement pull endpoint.
9. Implement client pull flow.
10. Implement push endpoint.
11. Implement server-side push merge inside DB transaction.
12. Implement client push confirmation merge.
13. Implement business APIs.
14. Ensure business APIs generate server-side HLC tokens.
15. Add sync scheduler and lifecycle triggers.
16. Add realtime notification trigger.
17. Add logging, diagnostics, retries, and tests.
```

---

# 20. Final Architecture Summary

```text
KMP local-first app:
  - writes all user changes to SQLite first
  - updates HLC per field group
  - tracks dirty field groups
  - runs pull-then-push sync
  - clears dirty state only after server confirmation

API server:
  - owns auth, validation, business rules, and HLC merge logic
  - exposes batch /sync APIs for local-first clients
  - exposes single-resource /api APIs for admin/web/backend clients
  - applies push merges inside DB transactions
  - locks rows before merging

PostgreSQL:
  - stores business data
  - stores hlc_map
  - stores soft-delete state
  - generates server_version
  - enforces constraints and persistence
```

This is the finalized canonical design.

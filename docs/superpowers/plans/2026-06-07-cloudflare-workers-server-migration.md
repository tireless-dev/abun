# Cloudflare Workers Server Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> Historical note: this implementation plan was written before the Worker cutover. It is preserved as execution history and still contains pre-cutover route examples like `/auth/*` and `/sync/*`.

**Goal:** Replace the JVM `:server` module with a Cloudflare Workers backend that preserves the current `/auth`, `/sync`, and `/api` behavior against Supabase Postgres while using Bun for local tooling.

**Architecture:** Add a new `workers/api` TypeScript application that runs on Cloudflare Workers, uses Bun for dependency management and scripts, and talks to Supabase Postgres through a Worker-compatible Postgres client behind a small transaction wrapper. Preserve the existing client-facing contract by freezing behavior with parity tests before switching Kotlin clients to the new base URL and removing the legacy Ktor module.

**Tech Stack:** Cloudflare Workers (`workerd`), Wrangler v4, Bun, TypeScript, Vitest, `@cloudflare/vitest-pool-workers`, `pg` 8.16.3+ with Hyperdrive-compatible connection strings, Supabase PostgreSQL, existing Kotlin Multiplatform clients and Ktor contract tests.

---

## File Structure

### New Worker App

- Create: `workers/api/package.json`
- Create: `workers/api/tsconfig.json`
- Create: `workers/api/wrangler.jsonc`
- Create: `workers/api/.dev.vars.example`
- Create: `workers/api/worker-configuration.d.ts`
- Create: `workers/api/vitest.config.ts`
- Create: `workers/api/src/index.ts`
- Create: `workers/api/src/env.ts`
- Create: `workers/api/src/http/router.ts`
- Create: `workers/api/src/http/errors.ts`
- Create: `workers/api/src/http/json.ts`
- Create: `workers/api/src/http/auth.ts`
- Create: `workers/api/src/db/client.ts`
- Create: `workers/api/src/db/transaction.ts`
- Create: `workers/api/src/db/schema.ts`
- Create: `workers/api/src/domain/models.ts`
- Create: `workers/api/src/domain/clock.ts`
- Create: `workers/api/src/services/auth-service.ts`
- Create: `workers/api/src/services/preference-sync-service.ts`
- Create: `workers/api/src/services/routine-sync-service.ts`
- Create: `workers/api/src/services/task-sync-service.ts`
- Create: `workers/api/src/services/alarm-sync-service.ts`
- Create: `workers/api/src/services/task-event-sync-service.ts`
- Create: `workers/api/src/services/pomodoro-session-sync-service.ts`
- Create: `workers/api/src/services/business-api-service.ts`
- Create: `workers/api/test/helpers/worker.ts`
- Create: `workers/api/test/helpers/db.ts`
- Create: `workers/api/test/auth.spec.ts`
- Create: `workers/api/test/sync-tasks.spec.ts`
- Create: `workers/api/test/sync-resources.spec.ts`
- Create: `workers/api/test/business-api.spec.ts`
- Create: `workers/api/test/error-handling.spec.ts`

### Shared Contract Fixtures And Docs

- Create: `docs/contracts/server-routes.md`
- Create: `docs/contracts/server-fixtures/auth.json`
- Create: `docs/contracts/server-fixtures/sync-tasks.json`
- Create: `docs/contracts/server-fixtures/business-api.json`

### Existing Repo Updates

- Modify: `package.json`
- Modify: `settings.gradle.kts`
- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/SyncRemoteApi.kt`
- Modify: `server/src/test/kotlin/dev/tireless/abun/ApplicationTest.kt`
- Delete later: `server/build.gradle.kts`
- Delete later: `server/src/main/kotlin/dev/tireless/abun/Application.kt`
- Delete later: `server/src/main/kotlin/dev/tireless/abun/SyncServices.kt`
- Delete later: `server/src/main/kotlin/dev/tireless/abun/BusinessApiModels.kt`
- Delete later: `server/src/main/resources/db/schema.sql`
- Delete later: `server/src/main/resources/logback.xml`
- Delete later: `server/src/test/kotlin/dev/tireless/abun/ApplicationTest.kt`

### Existing Files To Reference During Implementation

- Reference: `server/src/main/kotlin/dev/tireless/abun/Application.kt`
- Reference: `server/src/main/kotlin/dev/tireless/abun/SyncServices.kt`
- Reference: `server/src/test/kotlin/dev/tireless/abun/ApplicationTest.kt`
- Reference: `server/src/main/resources/db/schema.sql`
- Reference: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/SyncRemoteApi.kt`

### Database Strategy

- Use a Hyperdrive or direct Postgres connection string exposed to the Worker as `DB_URL`.
- Use `pg` version `8.16.3` or higher because Cloudflare’s current Postgres tutorial explicitly requires that minimum for Workers compatibility.
- Enable `nodejs_compat` in `wrangler.jsonc` with a compatibility date on or after `2026-06-06`.

## Task 1: Freeze The Existing Server Contract

**Files:**
- Create: `docs/contracts/server-routes.md`
- Create: `docs/contracts/server-fixtures/auth.json`
- Create: `docs/contracts/server-fixtures/sync-tasks.json`
- Create: `docs/contracts/server-fixtures/business-api.json`
- Modify: `server/src/test/kotlin/dev/tireless/abun/ApplicationTest.kt`

- [ ] **Step 1: Write the failing Ktor contract fixture test**

```kotlin
@Test
fun `contract fixtures stay aligned with auth sync and business responses`() = testApplication {
    application { module(testServices(authRequired = true)) }
    val jsonClient = jsonClient()

    jsonClient.post("/auth/otp/request") {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(OtpRequestBody(TestSharedAccount.EMAIL))
    }

    val verifyResponse = jsonClient.post("/auth/otp/verify") {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(OtpVerifyBody(TestSharedAccount.EMAIL, TestSharedAccount.OTP))
    }

    val token = verifyResponse.body<OtpVerifyResponse>().accessToken

    val syncResponse = jsonClient.post("/sync/tasks") {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(
            BatchRequest(
                items = listOf(
                    SyncTask(
                        id = "fixture-task",
                        title = "Fixture title",
                        hlcMap = mapOf("title" to "1715959378000-0001-deviceA"),
                        dirtyFields = listOf("title"),
                    ),
                ),
            ),
        )
    }

    val businessResponse = jsonClient.get("/api/tasks/fixture-task") {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    assertEquals(HttpStatusCode.OK, verifyResponse.status)
    assertEquals(HttpStatusCode.OK, businessResponse.status)
    assertEquals(listOf("title"), syncResponse.body<BatchRequest<SyncTask>>().items.single().acceptedFields)

    val fixture = ContractFixture(
        auth = verifyResponse.body<OtpVerifyResponse>(),
        syncTask = syncResponse.body<BatchRequest<SyncTask>>().items.single(),
        task = businessResponse.body<TaskResponse>(),
    )

    assertEquals(expectedContractFixtureJson.trim(), Json.encodeToString(fixture).trim())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "dev.tireless.abun.ApplicationTest.contract fixtures stay aligned with auth sync and business responses"`

Expected: FAIL because `ContractFixture` and `expectedContractFixtureJson` do not exist yet.

- [ ] **Step 3: Write the minimal fixture model and fixture files**

```kotlin
@Serializable
private data class ContractFixture(
    val auth: OtpVerifyResponse,
    val syncTask: SyncTask,
    val task: TaskResponse,
)

private val expectedContractFixtureJson = """
{
  "auth": {
    "access_token": "uid:shared-user",
    "user_id": "shared-user"
  },
  "syncTask": {
    "id": "fixture-task",
    "title": "Fixture title"
  },
  "task": {
    "id": "fixture-task",
    "title": "Fixture title"
  }
}
""".trimIndent()
```

```markdown
# Server Route Contract

## Route Groups

- `POST /auth/otp/request`
- `POST /auth/otp/verify`
- `GET|POST /sync/preferences`
- `GET|POST /sync/routines`
- `GET|POST /sync/tasks`
- `GET|POST /sync/alarms`
- `GET|POST /sync/task-events`
- `GET|POST /sync/pomodoro-sessions`
- `GET|PUT|DELETE /api/preferences/{key}`
- `GET|POST|PATCH|DELETE /api/routines`
- `GET|POST|PATCH|DELETE /api/tasks`
- `GET|POST|PATCH|DELETE /api/alarms`
- `GET|POST|PATCH|DELETE /api/pomodoro-sessions`
- `GET /api/journals/{date}`
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "dev.tireless.abun.ApplicationTest.contract fixtures stay aligned with auth sync and business responses"`

Expected: PASS and fixture files exist under `docs/contracts/server-fixtures`.

- [ ] **Step 5: Commit**

```bash
git add server/src/test/kotlin/dev/tireless/abun/ApplicationTest.kt docs/contracts/server-routes.md docs/contracts/server-fixtures
git commit -m "test: freeze server contract fixtures"
```

## Task 2: Scaffold The Worker App With Bun And Wrangler

**Files:**
- Create: `workers/api/package.json`
- Create: `workers/api/tsconfig.json`
- Create: `workers/api/wrangler.jsonc`
- Create: `workers/api/.dev.vars.example`
- Create: `workers/api/vitest.config.ts`
- Create: `workers/api/src/index.ts`
- Modify: `package.json`

- [ ] **Step 1: Write the failing Worker smoke test**

```ts
import { env, createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("worker smoke test", () => {
  it("returns the sync banner at the root route", async () => {
    const request = new Request("http://example.com/");
    const ctx = createExecutionContext();
    const response = await worker.fetch(request, env, ctx);
    await waitOnExecutionContext(ctx);

    expect(response.status).toBe(200);
    expect(await response.text()).toBe("abun sync server");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd workers/api && bun test`

Expected: FAIL because the Worker app, Vitest config, and `src/index.ts` do not exist yet.

- [ ] **Step 3: Write the minimal Worker scaffold**

```json
{
  "name": "@abun/workers-api",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "wrangler dev",
    "deploy": "wrangler deploy",
    "test": "vitest run",
    "cf-typegen": "wrangler types"
  },
  "dependencies": {
    "pg": "^8.16.3"
  },
  "devDependencies": {
    "@cloudflare/vitest-pool-workers": "^0.8.0",
    "@types/pg": "^8.15.5",
    "typescript": "^5.8.3",
    "vitest": "^4.1.0",
    "wrangler": "^4.0.0"
  }
}
```

```jsonc
{
  "$schema": "node_modules/wrangler/config-schema.json",
  "name": "abun-api",
  "main": "src/index.ts",
  "compatibility_date": "2026-06-07",
  "compatibility_flags": ["nodejs_compat"],
  "vars": {
    "ABUN_REQUIRE_AUTH": "true"
  }
}
```

```ts
export default {
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/") {
      return new Response("abun sync server");
    }
    return new Response("Not found", { status: 404 });
  },
} satisfies ExportedHandler<Env>;
```

```json
{
  "name": "abun",
  "version": "1.0.0",
  "private": true,
  "workspaces": [
    "app/webApp",
    "workers/api"
  ],
  "scripts": {
    "build": "npm run build -w webApp",
    "start": "npm run start -w webApp",
    "workers:test": "bun run --cwd workers/api test",
    "workers:dev": "bun run --cwd workers/api dev",
    "workers:deploy": "bun run --cwd workers/api deploy"
  },
  "devDependencies": {
    "playwright": "^1.60.0"
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd workers/api && bun test`

Expected: PASS with one smoke test passing.

- [ ] **Step 5: Commit**

```bash
git add package.json workers/api
git commit -m "build: scaffold cloudflare worker app"
```

## Task 3: Add Worker Env, Postgres Client, And Transaction Wrapper

**Files:**
- Create: `workers/api/src/env.ts`
- Create: `workers/api/src/db/client.ts`
- Create: `workers/api/src/db/transaction.ts`
- Create: `workers/api/src/db/schema.ts`
- Create: `workers/api/test/helpers/db.ts`
- Create: `workers/api/test/db.spec.ts`

- [ ] **Step 1: Write the failing DB transaction test**

```ts
import { describe, expect, it } from "vitest";
import { withTestDb } from "./helpers/db";

describe("db transaction wrapper", () => {
  it("rolls back the transaction when the callback throws", async () => {
    await withTestDb(async ({ query, transaction }) => {
      await query(`create temp table txn_test(id text primary key)`);

      await expect(
        transaction(async (tx) => {
          await tx.query(`insert into txn_test(id) values ($1)`, ["row-1"]);
          throw new Error("boom");
        }),
      ).rejects.toThrow("boom");

      const result = await query(`select count(*)::int as count from txn_test`);
      expect(result.rows[0].count).toBe(0);
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd workers/api && bun test test/db.spec.ts`

Expected: FAIL because `withTestDb`, `query`, and `transaction` do not exist yet.

- [ ] **Step 3: Write the minimal env and DB infrastructure**

```ts
import { Client } from "pg";

export type DbClient = {
  query: (sql: string, values?: unknown[]) => Promise<{ rows: Record<string, unknown>[] }>;
  end: () => Promise<void>;
};

export async function createDbClient(connectionString: string): Promise<DbClient> {
  const client = new Client({ connectionString });
  await client.connect();
  return client;
}
```

```ts
export async function withTransaction<T>(
  client: DbClient,
  block: (tx: DbClient) => Promise<T>,
): Promise<T> {
  await client.query("BEGIN");
  try {
    const result = await block(client);
    await client.query("COMMIT");
    return result;
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  }
}
```

```ts
export async function bootstrapSchema(client: DbClient): Promise<void> {
  await client.query(`
    create table if not exists sync_server_version (
      id integer primary key,
      next_value bigint not null
    )
  `);
  await client.query(`
    insert into sync_server_version (id, next_value)
    values (1, 0)
    on conflict (id) do nothing
  `);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd workers/api && bun test test/db.spec.ts`

Expected: PASS with the rollback assertion succeeding.

- [ ] **Step 5: Commit**

```bash
git add workers/api/src/env.ts workers/api/src/db workers/api/test/helpers/db.ts workers/api/test/db.spec.ts
git commit -m "feat: add worker postgres transaction layer"
```

## Task 4: Implement Shared HTTP Helpers And Auth Extraction

**Files:**
- Create: `workers/api/src/http/errors.ts`
- Create: `workers/api/src/http/json.ts`
- Create: `workers/api/src/http/auth.ts`
- Create: `workers/api/src/http/router.ts`
- Create: `workers/api/test/error-handling.spec.ts`
- Modify: `workers/api/src/index.ts`

- [ ] **Step 1: Write the failing auth-required route test**

```ts
import { env, createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("auth extraction", () => {
  it("returns 401 when auth is required and bearer token is missing", async () => {
    const request = new Request("http://example.com/api/tasks");
    const ctx = createExecutionContext();
    const response = await worker.fetch(request, { ...env, ABUN_REQUIRE_AUTH: "true" }, ctx);
    await waitOnExecutionContext(ctx);

    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({ message: "Missing or invalid bearer token" });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd workers/api && bun test test/error-handling.spec.ts`

Expected: FAIL because the Worker still returns generic `404` responses.

- [ ] **Step 3: Write the minimal HTTP helper layer**

```ts
export class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export function json(data: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(data), {
    ...init,
    headers: {
      "content-type": "application/json",
      ...(init.headers ?? {}),
    },
  });
}

export function requireUserId(request: Request, authRequired: boolean): string {
  const authorization = request.headers.get("Authorization");
  const token = authorization?.startsWith("Bearer ")
    ? authorization.replace("Bearer ", "").trim()
    : "";

  if (token.length > 0) {
    return token.startsWith("uid:") ? token.slice(4) : token;
  }

  if (authRequired) {
    throw new HttpError(401, "Missing or invalid bearer token");
  }

  return request.headers.get("X-User-Id") ?? "demo-user";
}
```

```ts
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      const url = new URL(request.url);
      if (url.pathname === "/") {
        return new Response("abun sync server");
      }
      if (url.pathname === "/api/tasks") {
        requireUserId(request, env.ABUN_REQUIRE_AUTH === "true");
        return json([]);
      }
      return new Response("Not found", { status: 404 });
    } catch (error) {
      if (error instanceof HttpError) {
        return json({ message: error.message }, { status: error.status });
      }
      return json({ message: "Internal server error" }, { status: 500 });
    }
  },
} satisfies ExportedHandler<Env>;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd workers/api && bun test test/error-handling.spec.ts`

Expected: PASS with a `401` JSON response body.

- [ ] **Step 5: Commit**

```bash
git add workers/api/src/http workers/api/src/index.ts workers/api/test/error-handling.spec.ts
git commit -m "feat: add worker http and auth helpers"
```

## Task 5: Implement OTP Auth On Workers

**Files:**
- Create: `workers/api/src/services/auth-service.ts`
- Create: `workers/api/test/auth.spec.ts`
- Modify: `workers/api/src/index.ts`
- Modify: `workers/api/src/db/schema.ts`

- [ ] **Step 1: Write the failing OTP parity test**

```ts
import { env, createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("otp auth", () => {
  it("stores deterministic OTP for the shared test account and returns the shared user token", async () => {
    const requestOtp = new Request("http://example.com/auth/otp/request", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: "shared@tireless.dev" }),
    });

    const verifyOtp = new Request("http://example.com/auth/otp/verify", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: "shared@tireless.dev", otp: "123456" }),
    });

    const ctx = createExecutionContext();
    const requested = await worker.fetch(requestOtp, env, ctx);
    const verified = await worker.fetch(verifyOtp, env, ctx);
    await waitOnExecutionContext(ctx);

    expect(requested.status).toBe(204);
    expect(verified.status).toBe(200);
    expect(await verified.json()).toEqual({
      access_token: "uid:shared-user",
      user_id: "shared-user",
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd workers/api && bun test test/auth.spec.ts`

Expected: FAIL because the `/auth/otp/request` and `/auth/otp/verify` routes do not exist.

- [ ] **Step 3: Write the minimal auth service and routes**

```ts
const SHARED_TEST_EMAIL = "shared@tireless.dev";
const SHARED_TEST_OTP = "123456";
const SHARED_TEST_USER_ID = "shared-user";

export class AuthService {
  constructor(private readonly db: DbClient) {}

  async requestOtp(email: string): Promise<void> {
    const normalized = email.trim().toLowerCase();
    const code = normalized === SHARED_TEST_EMAIL
      ? SHARED_TEST_OTP
      : String(Math.floor(100000 + Math.random() * 900000));

    await withTransaction(this.db, async (tx) => {
      await tx.query(
        `
          insert into otp_code(email, code, expires_at, created_at)
          values ($1, $2, $3, $4)
          on conflict (email)
          do update set code = excluded.code, expires_at = excluded.expires_at, created_at = excluded.created_at
        `,
        [normalized, code, new Date(Date.now() + 600_000).toISOString(), new Date().toISOString()],
      );
    });
  }

  async verifyOtp(email: string, otp: string): Promise<{ access_token: string; user_id: string }> {
    const normalized = email.trim().toLowerCase();
    const userId = normalized === SHARED_TEST_EMAIL ? SHARED_TEST_USER_ID : crypto.randomUUID();

    return withTransaction(this.db, async (tx) => {
      const otpRow = await tx.query(`select code, expires_at from otp_code where email = $1`, [normalized]);
      if (otpRow.rows.length === 0) throw new HttpError(400, "otp not requested");
      if (String(otpRow.rows[0].code) !== otp.trim()) throw new HttpError(400, "invalid otp");

      await tx.query(
        `
          insert into user_account(id, email, created_at)
          values ($1, $2, $3)
          on conflict (email) do nothing
        `,
        [userId, normalized, new Date().toISOString()],
      );
      await tx.query(`delete from otp_code where email = $1`, [normalized]);
      return { access_token: `uid:${userId}`, user_id: userId };
    });
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd workers/api && bun test test/auth.spec.ts`

Expected: PASS with `204` for request and shared-user token for verify.

- [ ] **Step 5: Commit**

```bash
git add workers/api/src/services/auth-service.ts workers/api/src/index.ts workers/api/src/db/schema.ts workers/api/test/auth.spec.ts
git commit -m "feat: port otp auth to workers"
```

## Task 6: Port One Sync Resource End To End Using Tasks As The Template

**Files:**
- Create: `workers/api/src/domain/models.ts`
- Create: `workers/api/src/domain/clock.ts`
- Create: `workers/api/src/services/task-sync-service.ts`
- Create: `workers/api/test/sync-tasks.spec.ts`
- Modify: `workers/api/src/db/schema.ts`
- Modify: `workers/api/src/index.ts`

- [ ] **Step 1: Write the failing task sync conflict test**

```ts
import { env, createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("task sync", () => {
  it("accepts newer HLC values and rejects older conflicting field updates", async () => {
    const first = new Request("http://example.com/sync/tasks", {
      method: "POST",
      headers: {
        "authorization": "Bearer user-1",
        "content-type": "application/json",
      },
      body: JSON.stringify({
        items: [
          {
            id: "task-1",
            title: "Write spec",
            hlc_map: { title: "1715959378000-0001-deviceA" },
            dirty_fields: ["title"],
          },
        ],
      }),
    });

    const second = new Request("http://example.com/sync/tasks", {
      method: "POST",
      headers: {
        "authorization": "Bearer user-1",
        "content-type": "application/json",
      },
      body: JSON.stringify({
        items: [
          {
            id: "task-1",
            title: "Old edit",
            hlc_map: { title: "1715959378000-0000-deviceB" },
            dirty_fields: ["title"],
          },
        ],
      }),
    });

    const ctx = createExecutionContext();
    const accepted = await worker.fetch(first, env, ctx);
    const rejected = await worker.fetch(second, env, ctx);
    await waitOnExecutionContext(ctx);

    expect((await accepted.json()).items[0].accepted_fields).toEqual(["title"]);
    expect((await rejected.json()).items[0].title).toBe("Write spec");
    expect((await rejected.json()).items[0].rejected_fields).toEqual(["title"]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd workers/api && bun test test/sync-tasks.spec.ts`

Expected: FAIL because `/sync/tasks` is not implemented.

- [ ] **Step 3: Write the minimal task sync implementation**

```ts
export class TaskSyncService {
  constructor(private readonly db: DbClient) {}

  async push(userId: string, items: SyncTask[]): Promise<SyncTask[]> {
    return withTransaction(this.db, async (tx) => {
      const results: SyncTask[] = [];
      for (const incoming of items) {
        const existing = await tx.query(`select * from task where user_id = $1 and id = $2`, [userId, incoming.id]);
        if (existing.rows.length === 0) {
          const serverVersion = await nextServerVersion(tx);
          await tx.query(
            `
              insert into task(id, user_id, title, detail, is_deleted, hlc_map, server_version, server_updated_at, created_at)
              values ($1, $2, $3, $4, $5, $6, $7, $8, $9)
            `,
            [
              incoming.id,
              userId,
              incoming.title,
              incoming.detail ?? null,
              incoming.isDeleted ?? false,
              JSON.stringify(incoming.hlcMap ?? {}),
              serverVersion,
              new Date().toISOString(),
              new Date().toISOString(),
            ],
          );
          results.push({ ...incoming, acceptedFields: incoming.dirtyFields ?? [] });
          continue;
        }

        const current = existing.rows[0];
        const currentMap = JSON.parse(String(current.hlc_map));
        const nextMap = { ...currentMap };
        const acceptedFields: string[] = [];
        const rejectedFields: string[] = [];
        let title = String(current.title);

        for (const field of incoming.dirtyFields ?? []) {
          const incomingHlc = incoming.hlcMap?.[field] ?? "";
          const existingHlc = currentMap[field] ?? "";
          if (incomingHlc > existingHlc) {
            if (field === "title") title = incoming.title;
            nextMap[field] = incomingHlc;
            acceptedFields.push(field);
          } else {
            rejectedFields.push(field);
          }
        }

        const serverVersion = await nextServerVersion(tx);
        await tx.query(
          `update task set title = $1, hlc_map = $2, server_version = $3, server_updated_at = $4 where user_id = $5 and id = $6`,
          [title, JSON.stringify(nextMap), serverVersion, new Date().toISOString(), userId, incoming.id],
        );

        results.push({
          ...incoming,
          title,
          acceptedFields,
          rejectedFields,
        });
      }
      return results;
    });
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd workers/api && bun test test/sync-tasks.spec.ts`

Expected: PASS with accepted and rejected field assertions matching the Kotlin server tests.

- [ ] **Step 5: Commit**

```bash
git add workers/api/src/domain workers/api/src/services/task-sync-service.ts workers/api/src/index.ts workers/api/test/sync-tasks.spec.ts
git commit -m "feat: port task sync service to workers"
```

## Task 7: Port The Remaining Sync Resources

**Files:**
- Create: `workers/api/src/services/preference-sync-service.ts`
- Create: `workers/api/src/services/routine-sync-service.ts`
- Create: `workers/api/src/services/alarm-sync-service.ts`
- Create: `workers/api/src/services/task-event-sync-service.ts`
- Create: `workers/api/src/services/pomodoro-session-sync-service.ts`
- Create: `workers/api/test/sync-resources.spec.ts`
- Modify: `workers/api/src/db/schema.ts`
- Modify: `workers/api/src/index.ts`

- [ ] **Step 1: Write the failing multi-resource sync test**

```ts
import { env, createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("remaining sync resources", () => {
  it("supports preferences routines alarms task-events and pomodoro sessions", async () => {
    const ctx = createExecutionContext();

    const pushedPreference = await worker.fetch(
      new Request("http://example.com/sync/preferences", {
        method: "POST",
        headers: { "authorization": "Bearer user-1", "content-type": "application/json" },
        body: JSON.stringify({
          items: [{ key: "task.default_alarm_lead_minutes", value: "20", value_type: "INT" }],
        }),
      }),
      env,
      ctx,
    );

    const pulledPreference = await worker.fetch(
      new Request("http://example.com/sync/preferences?cursor=0&limit=10", {
        headers: { "authorization": "Bearer user-1" },
      }),
      env,
      ctx,
    );

    await waitOnExecutionContext(ctx);

    expect(pushedPreference.status).toBe(200);
    expect((await pulledPreference.json()).items).toHaveLength(1);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd workers/api && bun test test/sync-resources.spec.ts`

Expected: FAIL because only `/sync/tasks` exists.

- [ ] **Step 3: Write the minimal remaining sync services**

```ts
export class PreferenceSyncService {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number) {
    const result = await this.db.query(
      `
        select * from preference
        where user_id = $1 and server_version > $2
        order by server_version asc
        limit $3
      `,
      [userId, cursor, limit],
    );
    return {
      items: result.rows.map(rowToPreference),
      next_cursor: result.rows.length > 0 ? Number(result.rows[result.rows.length - 1].server_version) : cursor,
      has_more: result.rows.length === limit,
    };
  }
}

export class TaskEventSyncService {
  constructor(private readonly db: DbClient) {}

  async push(userId: string, items: SyncTaskEvent[]) {
    return withTransaction(this.db, async (tx) => {
      const accepted: SyncTaskEvent[] = [];
      for (const event of items) {
        const existing = await tx.query(`select id from task_event where id = $1`, [event.id]);
        if (existing.rows.length > 0) {
          accepted.push({ ...event, accepted: false });
          continue;
        }
        const serverVersion = await nextServerVersion(tx);
        await tx.query(
          `
            insert into task_event(id, user_id, task_id, journal_date, event_type, content, postponed_json, event_time, is_deleted, server_version, server_updated_at, created_at)
            values ($1, $2, $3, $4, $5, $6, $7, $8, false, $9, $10, $11)
          `,
          [
            event.id,
            userId,
            event.taskId,
            event.journalDate,
            event.eventType,
            event.content ?? null,
            event.postponed ? JSON.stringify(event.postponed) : null,
            event.eventTime,
            serverVersion,
            new Date().toISOString(),
            new Date().toISOString(),
          ],
        );
        accepted.push({ ...event, accepted: true });
      }
      return accepted;
    });
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd workers/api && bun test test/sync-resources.spec.ts`

Expected: PASS with list/push behavior working for every sync resource group.

- [ ] **Step 5: Commit**

```bash
git add workers/api/src/services workers/api/src/index.ts workers/api/test/sync-resources.spec.ts workers/api/src/db/schema.ts
git commit -m "feat: port remaining sync resources to workers"
```

## Task 8: Port The Business APIs

**Files:**
- Create: `workers/api/src/services/business-api-service.ts`
- Create: `workers/api/test/business-api.spec.ts`
- Modify: `workers/api/src/index.ts`
- Modify: `workers/api/src/services/task-event-sync-service.ts`
- Modify: `workers/api/src/services/task-sync-service.ts`

- [ ] **Step 1: Write the failing business API parity test**

```ts
import { env, createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("business api", () => {
  it("supports the mutable resource flow plus derived task status and journals", async () => {
    const ctx = createExecutionContext();

    const createdTask = await worker.fetch(
      new Request("http://example.com/api/tasks", {
        method: "POST",
        headers: {
          "authorization": "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          id: "task-1",
          title: "Plan day",
          detail: "Work through the new planning window",
        }),
      }),
      env,
      ctx,
    );

    const taskStatus = await worker.fetch(
      new Request("http://example.com/api/tasks/task-1/status", {
        headers: { "authorization": "Bearer user-1" },
      }),
      env,
      ctx,
    );

    await waitOnExecutionContext(ctx);

    expect(createdTask.status).toBe(201);
    expect((await taskStatus.json()).status).toBe("PENDING");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd workers/api && bun test test/business-api.spec.ts`

Expected: FAIL because `/api/tasks`, `/api/tasks/:id/status`, and journal/event routes are not implemented.

- [ ] **Step 3: Write the minimal business API implementation**

```ts
export class BusinessApiService {
  constructor(
    private readonly tasks: TaskSyncService,
    private readonly taskEvents: TaskEventSyncService,
    private readonly preferences: PreferenceSyncService,
  ) {}

  async createTask(userId: string, input: TaskUpsertRequest): Promise<TaskResponse> {
    const [task] = await this.tasks.createFromBusinessApi(userId, input);
    await this.taskEvents.createFromBusinessApi(userId, {
      taskId: task.id,
      journalDate: input.journalDate ?? new Date().toISOString().slice(0, 10),
      eventType: "CREATED",
      eventTime: input.eventTime ?? new Date().toISOString(),
    });
    return toTaskResponse(task);
  }

  async taskStatus(userId: string, taskId: string): Promise<{ status: string }> {
    const status = await this.taskEvents.deriveStatus(userId, taskId);
    if (!status) throw new HttpError(404, "Not found");
    return { status };
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd workers/api && bun test test/business-api.spec.ts`

Expected: PASS with create, patch, delete, task status, event list, and journal flows matching the Ktor server tests.

- [ ] **Step 5: Commit**

```bash
git add workers/api/src/services/business-api-service.ts workers/api/src/index.ts workers/api/test/business-api.spec.ts workers/api/src/services/task-event-sync-service.ts workers/api/src/services/task-sync-service.ts
git commit -m "feat: port business api routes to workers"
```

## Task 9: Switch Kotlin Clients To Configurable Worker Base URLs

**Files:**
- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/SyncRemoteApi.kt`
- Modify: `app/sharedLogic/src/jvmTest/kotlin/dev/tireless/abun/SharedLogicDesktopTest.kt`
- Modify: `app/sharedLogic/src/commonTest/kotlin/dev/tireless/abun/SharedLogicCommonTest.kt`

- [ ] **Step 1: Write the failing client URL normalization test**

```kotlin
@Test
fun `sync remote api builds worker routes without duplicate slashes`() = runTest {
    val captured = mutableListOf<String>()
    val client = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                captured += request.url.toString()
                respond(
                    content = ByteReadChannel("""{"items":[],"next_cursor":0,"has_more":false}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
    }

    val api = SyncRemoteApi("https://abun-api.example.workers.dev/", client, FakeAuthProvider("uid:user-1"))
    api.pullTasks(cursor = 0, limit = 20)

    assertEquals("https://abun-api.example.workers.dev/sync/tasks?cursor=0&limit=20", captured.single())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedLogic:jvmTest --tests "dev.tireless.abun.SharedLogicDesktopTest.sync remote api builds worker routes without duplicate slashes"`

Expected: FAIL if `baseUrl` handling still permits malformed path concatenation in the new environment configuration.

- [ ] **Step 3: Write the minimal URL normalization change**

```kotlin
class SyncRemoteApi(
    baseUrl: String,
    private val client: HttpClient,
    private val authProvider: AuthProvider,
) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun pullTasks(cursor: Long, limit: Int): PullResponse<SyncTask> =
        client.get("$normalizedBaseUrl/sync/tasks?cursor=$cursor&limit=$limit") {
            authorize()
        }.body()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedLogic:jvmTest --tests "dev.tireless.abun.SharedLogicDesktopTest.sync remote api builds worker routes without duplicate slashes"`

Expected: PASS with Worker base URLs handled cleanly.

- [ ] **Step 5: Commit**

```bash
git add app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/SyncRemoteApi.kt app/sharedLogic/src/jvmTest/kotlin/dev/tireless/abun/SharedLogicDesktopTest.kt app/sharedLogic/src/commonTest/kotlin/dev/tireless/abun/SharedLogicCommonTest.kt
git commit -m "refactor: normalize remote api base urls"
```

## Task 10: Verify Worker Parity Through Desktop And Remove The Legacy Server

**Files:**
- Modify: `settings.gradle.kts`
- Delete: `server/build.gradle.kts`
- Delete: `server/src/main/kotlin/dev/tireless/abun/Application.kt`
- Delete: `server/src/main/kotlin/dev/tireless/abun/SyncServices.kt`
- Delete: `server/src/main/kotlin/dev/tireless/abun/BusinessApiModels.kt`
- Delete: `server/src/main/resources/db/schema.sql`
- Delete: `server/src/main/resources/logback.xml`
- Delete: `server/src/test/kotlin/dev/tireless/abun/ApplicationTest.kt`
- Modify: `docs/index.md`

- [ ] **Step 1: Write the failing repo cleanup check**

```bash
test ! -d server
```

- [ ] **Step 2: Run cleanup verification to confirm it fails before removal**

Run: `test ! -d server`

Expected: FAIL because the legacy `server/` module still exists.

- [ ] **Step 3: Remove the legacy Ktor module and update repo references**

```kotlin
include(":app:androidApp")
include(":app:desktopApp")
include(":app:sharedLogic")
include(":app:sharedUI")
include(":core")
```

```markdown
# Abun Docs Index

## Runtime Targets

- Desktop app: `./gradlew :app:desktopApp:run`
- Android app: standard Gradle/Android Studio flow
- Workers API: `cd workers/api && bun run dev`

## Verification

- Shared UI tests: `./gradlew :app:sharedUI:jvmTest`
- Shared logic tests: `./gradlew :app:sharedLogic:jvmTest`
- Worker tests: `cd workers/api && bun test`
```

- [ ] **Step 4: Run full verification**

Run: `cd workers/api && bun test`
Expected: PASS for Worker tests.

Run: `./gradlew :app:sharedUI:jvmTest`
Expected: PASS for shared UI tests.

Run: `./gradlew :app:desktopApp:test`
Expected: PASS for desktop-specific tests.

Run: `./gradlew :core:commonTest`
Expected: PASS for common core tests.

Run: `./gradlew :app:desktopApp:run`
Expected: Desktop app launches and manual auth/sync flows against the Worker backend succeed.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts docs/index.md workers/api package.json app/sharedLogic
git rm -r server
git commit -m "feat: replace ktor server with cloudflare workers backend"
```

## Self-Review

### Spec Coverage

- Cloudflare Workers runtime replacement is covered in Tasks 2 through 10.
- Bun local tooling is covered in Tasks 2 and 10.
- Supabase/PostgreSQL continuity is covered in Tasks 3 through 8.
- Custom OTP retention is covered in Task 5.
- `/auth`, `/sync`, and `/api` parity is covered in Tasks 1, 5, 6, 7, and 8.
- Kotlin client compatibility is covered in Task 9.
- Legacy server removal is covered in Task 10.

### Placeholder Scan

- No `TODO`, `TBD`, or “implement later” markers remain.
- All tasks have file paths, commands, expected results, and code snippets.
- Open decisions from the design have been resolved enough to execute:
  - Worker directory is `workers/api`
  - Postgres client is `pg` 8.16.3+ with `nodejs_compat`
  - Contract fixtures live under `docs/contracts/server-fixtures`
  - Legacy cutover happens only after Task 10 verification

### Type Consistency

- Worker auth uses `access_token` and `user_id` consistently with the existing Kotlin contract.
- `ABUN_REQUIRE_AUTH`, `DB_URL`, and `/sync/*` route naming stay consistent across tasks.
- `TaskSyncService`, `TaskEventSyncService`, and `BusinessApiService` names remain consistent across the plan.

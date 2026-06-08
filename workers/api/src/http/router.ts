import {
  hasDbUrl,
  type WorkerEnv,
} from "../env";
import { createDbClient } from "../db/client";
import { resolveAuthService } from "../services/auth-service";
import {
  requireBearerToken,
  resolveUserId,
} from "./auth";
import { HttpError } from "./errors";
import { json } from "./json";
import {
  resolvePreferenceSyncService,
  type SyncPreference,
} from "../services/preference-sync-service";
import {
  resolveRoutineSyncService,
  type SyncRoutine,
} from "../services/routine-sync-service";
import {
  resolveAlarmSyncService,
  type SyncAlarm,
} from "../services/alarm-sync-service";
import {
  resolveTaskEventSyncService,
  type SyncTaskEvent,
} from "../services/task-event-sync-service";
import {
  resolvePomodoroSessionSyncService,
  type SyncPomodoroSession,
} from "../services/pomodoro-session-sync-service";
import {
  resolveTaskSyncService,
  type SyncTask,
} from "../services/task-sync-service";
import type { BatchRequest } from "../services/sync-service-types";

export async function routeRequest(
  request: Request,
  env: Partial<WorkerEnv>,
): Promise<Response> {
  const { method } = request;
  const url = new URL(request.url);

  if (method === "GET" && url.pathname === "/") {
    return new Response("abun sync server");
  }

  if (method === "POST" && url.pathname === "/auth/otp/request") {
    const body = await readJsonBody(request);
    const email = readRequiredString(body, "email");
    return withOptionalDbClient(env, async (dbClient) => {
      const auth = resolveAuthService(env as Record<string, unknown>, dbClient ?? undefined);
      await auth.requestOtp(email);
      return new Response(null, { status: 204 });
    });
  }

  if (method === "POST" && url.pathname === "/auth/otp/verify") {
    const body = await readJsonBody(request);
    const email = readRequiredString(body, "email");
    const otp = readRequiredString(body, "otp");
    return withOptionalDbClient(env, async (dbClient) => {
      const auth = resolveAuthService(env as Record<string, unknown>, dbClient ?? undefined);
      const result = await auth.verifyOtp(email, otp);

      return json({
        access_token: result.accessToken,
        user_id: result.userId,
      });
    });
  }

  if (url.pathname === "/sync/tasks") {
    const userId = resolveUserId(request, env);
    return withOptionalDbClient(env, async (dbClient) => {
      const tasks = resolveTaskSyncService(env as Record<string, unknown>, dbClient ?? undefined);

      if (method === "GET") {
        const cursor = Number(url.searchParams.get("cursor") ?? "0");
        const limit = clampLimit(Number(url.searchParams.get("limit") ?? "500"));
        return json(await tasks.list(userId, Number.isFinite(cursor) ? cursor : 0, limit));
      }

      if (method === "POST") {
        const body = await readJsonBody(request);
        const items = readBatchItems<SyncTask>(body);
        return json({
          items: await tasks.push(userId, items),
        } satisfies BatchRequest<SyncTask>);
      }

      throw new HttpError(404, "Not found");
    });
  }

  if (url.pathname === "/sync/preferences") {
    const userId = resolveUserId(request, env);
    return withOptionalDbClient(env, async (dbClient) => {
      const preferences = resolvePreferenceSyncService(env as Record<string, unknown>, dbClient ?? undefined);

      if (method === "GET") {
        const cursor = Number(url.searchParams.get("cursor") ?? "0");
        const limit = clampLimit(Number(url.searchParams.get("limit") ?? "500"));
        return json(await preferences.list(userId, Number.isFinite(cursor) ? cursor : 0, limit));
      }

      if (method === "POST") {
        const body = await readJsonBody(request);
        return json({
          items: await preferences.push(userId, readBatchItems<SyncPreference>(body)),
        } satisfies BatchRequest<SyncPreference>);
      }

      throw new HttpError(404, "Not found");
    });
  }

  if (url.pathname === "/sync/routines") {
    const userId = resolveUserId(request, env);
    return withOptionalDbClient(env, async (dbClient) => {
      const routines = resolveRoutineSyncService(env as Record<string, unknown>, dbClient ?? undefined);

      if (method === "GET") {
        const cursor = Number(url.searchParams.get("cursor") ?? "0");
        const limit = clampLimit(Number(url.searchParams.get("limit") ?? "500"));
        return json(await routines.list(userId, Number.isFinite(cursor) ? cursor : 0, limit));
      }

      if (method === "POST") {
        const body = await readJsonBody(request);
        return json({
          items: await routines.push(userId, readBatchItems<SyncRoutine>(body)),
        } satisfies BatchRequest<SyncRoutine>);
      }

      throw new HttpError(404, "Not found");
    });
  }

  if (url.pathname === "/sync/alarms") {
    const userId = resolveUserId(request, env);
    return withOptionalDbClient(env, async (dbClient) => {
      const tasks = resolveTaskSyncService(env as Record<string, unknown>, dbClient ?? undefined);
      const alarms = resolveAlarmSyncService(env as Record<string, unknown>, tasks, dbClient ?? undefined);

      if (method === "GET") {
        const cursor = Number(url.searchParams.get("cursor") ?? "0");
        const limit = clampLimit(Number(url.searchParams.get("limit") ?? "500"));
        return json(await alarms.list(userId, Number.isFinite(cursor) ? cursor : 0, limit));
      }

      if (method === "POST") {
        const body = await readJsonBody(request);
        return json({
          items: await alarms.push(userId, readBatchItems<SyncAlarm>(body)),
        } satisfies BatchRequest<SyncAlarm>);
      }

      throw new HttpError(404, "Not found");
    });
  }

  if (url.pathname === "/sync/task-events") {
    const userId = resolveUserId(request, env);
    return withOptionalDbClient(env, async (dbClient) => {
      const tasks = resolveTaskSyncService(env as Record<string, unknown>, dbClient ?? undefined);
      const taskEvents = resolveTaskEventSyncService(env as Record<string, unknown>, tasks, dbClient ?? undefined);

      if (method === "GET") {
        const cursor = Number(url.searchParams.get("cursor") ?? "0");
        const limit = clampLimit(Number(url.searchParams.get("limit") ?? "500"));
        return json(await taskEvents.list(userId, Number.isFinite(cursor) ? cursor : 0, limit));
      }

      if (method === "POST") {
        const body = await readJsonBody(request);
        return json({
          items: await taskEvents.push(userId, readBatchItems<SyncTaskEvent>(body)),
        } satisfies BatchRequest<SyncTaskEvent>);
      }

      throw new HttpError(404, "Not found");
    });
  }

  if (url.pathname === "/sync/pomodoro-sessions") {
    const userId = resolveUserId(request, env);
    return withOptionalDbClient(env, async (dbClient) => {
      const tasks = resolveTaskSyncService(env as Record<string, unknown>, dbClient ?? undefined);
      const sessions = resolvePomodoroSessionSyncService(env as Record<string, unknown>, tasks, dbClient ?? undefined);

      if (method === "GET") {
        const cursor = Number(url.searchParams.get("cursor") ?? "0");
        const limit = clampLimit(Number(url.searchParams.get("limit") ?? "500"));
        return json(await sessions.list(userId, Number.isFinite(cursor) ? cursor : 0, limit));
      }

      if (method === "POST") {
        const body = await readJsonBody(request);
        return json({
          items: await sessions.push(userId, readBatchItems<SyncPomodoroSession>(body)),
        } satisfies BatchRequest<SyncPomodoroSession>);
      }

      throw new HttpError(404, "Not found");
    });
  }

  if (method === "GET" && url.pathname === "/api/tasks") {
    requireBearerToken(request, env);
    return json([]);
  }

  throw new HttpError(404, "Not found");
}

async function readJsonBody(request: Request): Promise<Record<string, unknown>> {
  let body: unknown;

  try {
    body = await request.json();
  } catch {
    throw new HttpError(400, "Invalid JSON body");
  }

  if (typeof body !== "object" || body === null || Array.isArray(body)) {
    throw new HttpError(400, "Invalid JSON body");
  }

  return body as Record<string, unknown>;
}

function readRequiredString(
  body: Record<string, unknown>,
  field: string,
): string {
  const value = body[field];

  if (typeof value !== "string") {
    throw new HttpError(400, `${field} is required`);
  }

  return value;
}

function readBatchItems<T>(body: Record<string, unknown>): T[] {
  const items = body.items;
  if (!Array.isArray(items)) {
    throw new HttpError(400, "items is required");
  }
  return items as T[];
}

function clampLimit(value: number): number {
  if (!Number.isFinite(value)) return 500;
  return Math.min(500, Math.max(1, Math.trunc(value)));
}

async function withOptionalDbClient(
  env: Partial<WorkerEnv>,
  block: (dbClient: Awaited<ReturnType<typeof createDbClient>> | null) => Promise<Response>,
): Promise<Response> {
  if (!hasDbUrl(env)) {
    return block(null);
  }

  const dbClient = await createDbClient(env);
  try {
    return await block(dbClient);
  } finally {
    await dbClient.end?.();
  }
}

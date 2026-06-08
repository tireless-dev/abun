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
  BusinessApiService,
  type AlarmPatchRequest,
  type AlarmUpsertRequest,
  type PomodoroSessionPatchRequest,
  type PomodoroSessionUpsertRequest,
  type PreferencePutRequest,
  type RoutinePatchRequest,
  type RoutineUpsertRequest,
  type TaskEventCreateRequest,
  type TaskPatchRequest,
  type TaskUpsertRequest,
} from "../services/business-api-service";
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

  if (url.pathname.startsWith("/api")) {
    const userId = resolveUserId(request, env);
    return withOptionalDbClient(env, async (dbClient) => {
      const tasks = resolveTaskSyncService(env as Record<string, unknown>, dbClient ?? undefined);
      const preferences = resolvePreferenceSyncService(env as Record<string, unknown>, dbClient ?? undefined);
      const routines = resolveRoutineSyncService(env as Record<string, unknown>, dbClient ?? undefined);
      const alarms = resolveAlarmSyncService(env as Record<string, unknown>, tasks, dbClient ?? undefined);
      const taskEvents = resolveTaskEventSyncService(env as Record<string, unknown>, tasks, dbClient ?? undefined);
      const sessions = resolvePomodoroSessionSyncService(env as Record<string, unknown>, tasks, dbClient ?? undefined);
      const api = new BusinessApiService(preferences, routines, tasks, alarms, taskEvents, sessions);
      return routeBusinessRequest(request, url, method, userId, api);
    });
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
    if (!env.HYPERDRIVE) {
      await dbClient.end?.();
    }
  }
}

async function routeBusinessRequest(
  request: Request,
  url: URL,
  method: string,
  userId: string,
  api: BusinessApiService,
): Promise<Response> {
  const segments = url.pathname.split("/").filter(Boolean);
  if (segments[0] !== "api") {
    throw new HttpError(404, "Not found");
  }

  if (segments[1] === "preferences") {
    if (segments.length === 2 && method === "GET") {
      return json((await api.listPreferences(userId)).map(toPreferenceResponse));
    }
    const key = segments[2];
    if (!key) throw new HttpError(404, "Not found");
    if (segments.length !== 3) throw new HttpError(404, "Not found");
    if (method === "GET") {
      const preference = await api.getPreference(userId, key);
      if (!preference) return new Response(null, { status: 404 });
      return json(toPreferenceResponse(preference));
    }
    if (method === "PUT") {
      return json(toPreferenceResponse(await api.putPreference(userId, key, await readJsonBody(request) as unknown as PreferencePutRequest)));
    }
    if (method === "DELETE") {
      const preference = await api.deletePreference(userId, key);
      if (!preference) return new Response(null, { status: 404 });
      return json(toPreferenceResponse(preference));
    }
    throw new HttpError(404, "Not found");
  }

  if (segments[1] === "routines") {
    if (segments.length === 2 && method === "GET") {
      return json((await api.listRoutines(userId)).map(toRoutineResponse));
    }
    if (segments.length === 2 && method === "POST") {
      return json(toRoutineResponse(await api.createRoutine(userId, await readJsonBody(request) as unknown as RoutineUpsertRequest)), { status: 201 });
    }
    const id = segments[2];
    if (!id || segments.length !== 3) throw new HttpError(404, "Not found");
    if (method === "GET") {
      const routine = await api.getRoutine(userId, id);
      if (!routine) return new Response(null, { status: 404 });
      return json(toRoutineResponse(routine));
    }
    if (method === "PATCH") {
      const routine = await api.patchRoutine(userId, id, await readJsonBody(request) as unknown as RoutinePatchRequest);
      if (!routine) return new Response(null, { status: 404 });
      return json(toRoutineResponse(routine));
    }
    if (method === "DELETE") {
      const routine = await api.deleteRoutine(userId, id);
      if (!routine) return new Response(null, { status: 404 });
      return json(toRoutineResponse(routine));
    }
    throw new HttpError(404, "Not found");
  }

  if (segments[1] === "tasks") {
    if (segments.length === 2 && method === "GET") {
      return json((await api.listTasks(userId)).map(toTaskResponse));
    }
    if (segments.length === 2 && method === "POST") {
      return json(toTaskResponse(await api.createTask(userId, await readJsonBody(request) as unknown as TaskUpsertRequest)), { status: 201 });
    }
    const id = segments[2];
    if (!id) throw new HttpError(404, "Not found");
    if (segments.length === 3 && method === "GET") {
      const task = await api.getTask(userId, id);
      if (!task) return new Response(null, { status: 404 });
      return json(toTaskResponse(task));
    }
    if (segments.length === 3 && method === "PATCH") {
      const task = await api.patchTask(userId, id, await readJsonBody(request) as unknown as TaskPatchRequest);
      if (!task) return new Response(null, { status: 404 });
      return json(toTaskResponse(task));
    }
    if (segments.length === 3 && method === "DELETE") {
      const task = await api.deleteTask(userId, id);
      if (!task) return new Response(null, { status: 404 });
      return json(toTaskResponse(task));
    }
    if (segments.length === 4 && segments[3] === "status" && method === "GET") {
      const status = await api.deriveTaskStatus(userId, id);
      if (!status) return new Response(null, { status: 404 });
      return json({ status });
    }
    if (segments.length === 4 && segments[3] === "events" && method === "GET") {
      const events = await api.listTaskEvents(userId, id);
      if (!events) return new Response(null, { status: 404 });
      return json(events.map(toTaskEventResponse));
    }
    if (segments.length === 4 && segments[3] === "events" && method === "POST") {
      const created = await api.createTaskEvent(userId, id, await readJsonBody(request) as unknown as TaskEventCreateRequest);
      return json(toTaskEventResponse(created), { status: 201 });
    }
    throw new HttpError(404, "Not found");
  }

  if (segments[1] === "alarms") {
    if (segments.length === 2 && method === "GET") {
      return json((await api.listAlarms(userId)).map(toAlarmResponse));
    }
    if (segments.length === 2 && method === "POST") {
      return json(toAlarmResponse(await api.createAlarm(userId, await readJsonBody(request) as unknown as AlarmUpsertRequest)), { status: 201 });
    }
    const id = segments[2];
    if (!id || segments.length !== 3) throw new HttpError(404, "Not found");
    if (method === "GET") {
      const alarm = await api.getAlarm(userId, id);
      if (!alarm) return new Response(null, { status: 404 });
      return json(toAlarmResponse(alarm));
    }
    if (method === "PATCH") {
      const alarm = await api.patchAlarm(userId, id, await readJsonBody(request) as unknown as AlarmPatchRequest);
      if (!alarm) return new Response(null, { status: 404 });
      return json(toAlarmResponse(alarm));
    }
    if (method === "DELETE") {
      const alarm = await api.deleteAlarm(userId, id);
      if (!alarm) return new Response(null, { status: 404 });
      return json(toAlarmResponse(alarm));
    }
    throw new HttpError(404, "Not found");
  }

  if (segments[1] === "pomodoro-sessions") {
    if (segments.length === 2 && method === "GET") {
      return json((await api.listPomodoroSessions(userId)).map(toPomodoroSessionResponse));
    }
    if (segments.length === 2 && method === "POST") {
      return json(toPomodoroSessionResponse(await api.createPomodoroSession(userId, await readJsonBody(request) as unknown as PomodoroSessionUpsertRequest)), { status: 201 });
    }
    const id = segments[2];
    if (!id || segments.length !== 3) throw new HttpError(404, "Not found");
    if (method === "GET") {
      const session = await api.getPomodoroSession(userId, id);
      if (!session) return new Response(null, { status: 404 });
      return json(toPomodoroSessionResponse(session));
    }
    if (method === "PATCH") {
      const session = await api.patchPomodoroSession(userId, id, await readJsonBody(request) as unknown as PomodoroSessionPatchRequest);
      if (!session) return new Response(null, { status: 404 });
      return json(toPomodoroSessionResponse(session));
    }
    if (method === "DELETE") {
      const session = await api.deletePomodoroSession(userId, id);
      if (!session) return new Response(null, { status: 404 });
      return json(toPomodoroSessionResponse(session));
    }
    throw new HttpError(404, "Not found");
  }

  if (segments[1] === "journals" && segments[2] && segments.length === 3 && method === "GET") {
    return json(await api.journal(userId, segments[2]));
  }

  throw new HttpError(404, "Not found");
}

function toTaskResponse(task: SyncTask): Record<string, unknown> {
  return {
    id: task.id,
    parent_id: task.parent_id ?? null,
    routine_id: task.routine_id ?? null,
    title: task.title,
    detail: task.detail ?? null,
    start_not_before: task.start_not_before ?? null,
    end_not_after: task.end_not_after ?? null,
    estimated_duration: task.estimated_duration ?? null,
    is_deleted: task.is_deleted ?? false,
    server_version: task.server_version ?? 0,
    server_updated_at: task.server_updated_at ?? null,
    created_at: task.created_at ?? null,
  };
}

function toRoutineResponse(routine: SyncRoutine): Record<string, unknown> {
  return {
    id: routine.id,
    template_title: routine.template_title,
    template_detail: routine.template_detail ?? null,
    recurrence_rule: routine.recurrence_rule,
    default_start_not_before: routine.default_start_not_before ?? null,
    default_estimated_duration: routine.default_estimated_duration ?? null,
    is_active: routine.is_active ?? true,
    is_deleted: routine.is_deleted ?? false,
    server_version: routine.server_version ?? 0,
    server_updated_at: routine.server_updated_at ?? null,
    created_at: routine.created_at ?? null,
  };
}

function toAlarmResponse(alarm: SyncAlarm): Record<string, unknown> {
  return {
    id: alarm.id,
    task_id: alarm.task_id,
    trigger_time: alarm.trigger_time,
    is_active: alarm.is_active ?? true,
    is_deleted: alarm.is_deleted ?? false,
    server_version: alarm.server_version ?? 0,
    server_updated_at: alarm.server_updated_at ?? null,
    created_at: alarm.created_at ?? null,
  };
}

function toPomodoroSessionResponse(session: SyncPomodoroSession): Record<string, unknown> {
  return {
    id: session.id,
    task_id: session.task_id ?? null,
    phase: session.phase,
    state: session.state,
    started_at: session.started_at,
    ends_at: session.ends_at,
    completed_at: session.completed_at ?? null,
    duration_minutes: session.duration_minutes,
    note: session.note ?? null,
    task_update: session.task_update ?? "NONE",
    is_deleted: session.is_deleted ?? false,
    server_version: session.server_version ?? 0,
    server_updated_at: session.server_updated_at ?? null,
    created_at: session.created_at ?? null,
  };
}

function toPreferenceResponse(preference: SyncPreference): Record<string, unknown> {
  return {
    key: preference.key,
    value: preference.value ?? null,
    value_type: preference.value_type,
    is_deleted: preference.is_deleted ?? false,
    server_version: preference.server_version ?? 0,
    server_updated_at: preference.server_updated_at ?? null,
    created_at: preference.created_at ?? null,
  };
}

function toTaskEventResponse(event: SyncTaskEvent): Record<string, unknown> {
  return {
    id: event.id,
    task_id: event.task_id,
    journal_date: event.journal_date,
    event_type: event.event_type,
    content: event.content ?? null,
    postponed: event.postponed ?? null,
    event_time: event.event_time,
    is_deleted: event.is_deleted ?? false,
    server_version: event.server_version ?? 0,
    server_updated_at: event.server_updated_at ?? null,
    created_at: event.created_at ?? null,
  };
}

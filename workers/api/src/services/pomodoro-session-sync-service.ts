import type { DbClient } from "../db/transaction";
import { withTransaction } from "../db/transaction";
import type {
  MutableSyncService,
  PullResponse,
} from "./sync-service-types";
import type { TaskSyncServiceLike } from "./task-sync-service";
import {
  dedupe,
  nextServerVersion,
  parseStringMap,
  shouldAcceptIncoming,
  stringOrUndefined,
} from "./sync-utils";

export interface SyncPomodoroSession {
  id: string;
  task_id?: string | null;
  phase: string;
  state: string;
  started_at: string;
  ends_at: string;
  completed_at?: string | null;
  duration_minutes: number;
  note?: string | null;
  task_update?: string;
  is_deleted?: boolean;
  hlc_map?: Record<string, string>;
  dirty_fields?: string[];
  accepted_fields?: string[] | null;
  rejected_fields?: string[] | null;
  server_version?: number;
  server_updated_at?: string | null;
  created_at?: string | null;
}

export interface PomodoroSessionSyncServiceLike extends MutableSyncService<SyncPomodoroSession> {}

const POMODORO_FIELDS = new Set([
  "task",
  "phase",
  "phase",
  "state",
  "timing",
  "started_at",
  "ends_at",
  "completed_at",
  "duration_minutes",
  "note",
  "outcome",
  "task_update",
  "delete",
]);

export class PomodoroSessionSyncService implements PomodoroSessionSyncServiceLike {
  private nextVersionValue = 0;
  private readonly sessionsByUser = new Map<string, Map<string, SyncPomodoroSession>>();

  constructor(private readonly tasks: TaskSyncServiceLike) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncPomodoroSession>> {
    const items = Array.from(this.userSessions(userId).values())
      .filter((session) => (session.server_version ?? 0) > cursor)
      .sort((left, right) => (left.server_version ?? 0) - (right.server_version ?? 0))
      .slice(0, limit);

    return {
      items: items.map(cloneSession),
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncPomodoroSession[]): Promise<SyncPomodoroSession[]> {
    const results: SyncPomodoroSession[] = [];

    for (const incoming of items) {
      if (incoming.task_id && !(await this.tasks.exists(userId, incoming.task_id))) {
        throw new Error("task_id does not belong to current user");
      }

      const sessions = this.userSessions(userId);
      const existing = sessions.get(incoming.id);
      if (!existing) {
        const inserted = this.insertNew(incoming);
        sessions.set(inserted.id, inserted);
        results.push(cloneSession(inserted));
        continue;
      }

      const merged = this.mergeExisting(existing, incoming);
      sessions.set(merged.id, merged);
      results.push(cloneSession(merged));
    }

    return results;
  }

  private insertNew(incoming: SyncPomodoroSession): SyncPomodoroSession {
    const now = new Date().toISOString();
    return {
      ...cloneSession(incoming),
      task_update: incoming.task_update ?? "NONE",
      is_deleted: incoming.is_deleted ?? false,
      accepted_fields: dedupe(incoming.dirty_fields ?? []),
      rejected_fields: [],
      server_version: this.nextVersion(),
      server_updated_at: now,
      created_at: incoming.created_at ?? now,
    };
  }

  private mergeExisting(existing: SyncPomodoroSession, incoming: SyncPomodoroSession): SyncPomodoroSession {
    let merged = cloneSession(existing);
    const accepted: string[] = [];
    const rejected: string[] = [];

    for (const field of dedupe(incoming.dirty_fields ?? [])) {
      if (!POMODORO_FIELDS.has(field)) {
        rejected.push(field);
        continue;
      }

      const incomingHlc = incoming.hlc_map?.[field];
      const existingHlc = existing.hlc_map?.[field];
      if (!shouldAcceptIncoming(incomingHlc, existingHlc)) {
        rejected.push(field);
        continue;
      }

      switch (field) {
        case "task":
          merged.task_id = incoming.task_id ?? undefined;
          break;
        case "phase":
          merged.phase = incoming.phase;
          break;
        case "timing":
          merged.phase = incoming.phase;
          merged.started_at = incoming.started_at;
          merged.ends_at = incoming.ends_at;
          merged.duration_minutes = incoming.duration_minutes;
          break;
        case "state":
          merged.state = incoming.state;
          break;
        case "started_at":
          merged.started_at = incoming.started_at;
          break;
        case "ends_at":
          merged.ends_at = incoming.ends_at;
          break;
        case "completed_at":
          merged.completed_at = incoming.completed_at ?? undefined;
          break;
        case "duration_minutes":
          merged.duration_minutes = incoming.duration_minutes;
          break;
        case "note":
          merged.note = incoming.note ?? undefined;
          break;
        case "task_update":
        case "outcome":
          merged.task_update = incoming.task_update ?? "NONE";
          break;
        case "delete":
          merged.is_deleted = incoming.is_deleted ?? false;
          break;
      }

      merged.hlc_map = {
        ...(merged.hlc_map ?? {}),
        [field]: incomingHlc!,
      };
      accepted.push(field);
    }

    if (accepted.length === 0) {
      return {
        ...merged,
        accepted_fields: [],
        rejected_fields: rejected,
      };
    }

    return {
      ...merged,
      accepted_fields: accepted,
      rejected_fields: rejected,
      server_version: this.nextVersion(),
      server_updated_at: new Date().toISOString(),
      created_at: existing.created_at ?? new Date().toISOString(),
    };
  }

  private nextVersion(): number {
    this.nextVersionValue += 1;
    return this.nextVersionValue;
  }

  private userSessions(userId: string): Map<string, SyncPomodoroSession> {
    let sessions = this.sessionsByUser.get(userId);
    if (!sessions) {
      sessions = new Map();
      this.sessionsByUser.set(userId, sessions);
    }
    return sessions;
  }
}

let defaultPomodoroSessionSyncService: PomodoroSessionSyncServiceLike | null = null;

class DatabasePomodoroSessionSyncService implements PomodoroSessionSyncServiceLike {
  constructor(
    private readonly db: DbClient,
    private readonly tasks: TaskSyncServiceLike,
  ) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncPomodoroSession>> {
    const result = await this.db.query(
      `
        select * from pomodoro_session
        where user_id = $1 and server_version > $2
        order by server_version asc
        limit $3
      `,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToSyncPomodoroSession);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncPomodoroSession[]): Promise<SyncPomodoroSession[]> {
    const results: SyncPomodoroSession[] = [];
    for (const incoming of items) {
      const result = await withTransaction(this.db, async (tx) => {
        if (incoming.task_id && !(await this.tasks.exists(userId, incoming.task_id))) {
          throw new Error("task_id does not belong to current user");
        }
        const existing = await selectOnePomodoroSession(tx, userId, incoming.id);
        if (!existing) return insertNewPomodoroSession(tx, userId, incoming);
        return mergeExistingPomodoroSession(tx, userId, existing, incoming);
      });
      results.push(result);
    }
    return results;
  }
}

export function resolvePomodoroSessionSyncService(
  env: Record<string, unknown>,
  tasks: TaskSyncServiceLike,
  dbClient?: DbClient,
): PomodoroSessionSyncServiceLike {
  const injected = env.__pomodoroSessionSyncService;
  if (isPomodoroSessionSyncServiceLike(injected)) {
    return injected;
  }

  if (dbClient) {
    return new DatabasePomodoroSessionSyncService(dbClient, tasks);
  }

  defaultPomodoroSessionSyncService ??= new PomodoroSessionSyncService(tasks);
  return defaultPomodoroSessionSyncService;
}

function isPomodoroSessionSyncServiceLike(value: unknown): value is PomodoroSessionSyncServiceLike {
  return (
    typeof value === "object" &&
    value !== null &&
    "list" in value &&
    typeof value.list === "function" &&
    "push" in value &&
    typeof value.push === "function"
  );
}

function cloneSession(session: SyncPomodoroSession): SyncPomodoroSession {
  return {
    ...session,
    task_id: session.task_id ?? undefined,
    completed_at: session.completed_at ?? undefined,
    note: session.note ?? undefined,
    task_update: session.task_update ?? "NONE",
    is_deleted: session.is_deleted ?? false,
    hlc_map: { ...(session.hlc_map ?? {}) },
    dirty_fields: [...(session.dirty_fields ?? [])],
    accepted_fields: session.accepted_fields ? [...session.accepted_fields] : undefined,
    rejected_fields: session.rejected_fields ? [...session.rejected_fields] : undefined,
    server_updated_at: session.server_updated_at ?? undefined,
    created_at: session.created_at ?? undefined,
  };
}

async function selectOnePomodoroSession(db: DbClient, userId: string, id: string): Promise<SyncPomodoroSession | null> {
  const result = await db.query(
    `select * from pomodoro_session where user_id = $1 and id = $2`,
    [userId, id],
  );
  const row = result.rows[0];
  return row ? rowToSyncPomodoroSession(row) : null;
}

async function insertNewPomodoroSession(
  db: DbClient,
  userId: string,
  incoming: SyncPomodoroSession,
): Promise<SyncPomodoroSession> {
  const now = new Date().toISOString();
  const canonical: SyncPomodoroSession = {
    ...cloneSession(incoming),
    task_update: incoming.task_update ?? "NONE",
    is_deleted: incoming.is_deleted ?? false,
    accepted_fields: dedupe(incoming.dirty_fields ?? []),
    rejected_fields: [],
    server_version: await nextServerVersion(db),
    server_updated_at: now,
    created_at: incoming.created_at ?? now,
  };
  await db.query(
    `
      insert into pomodoro_session(
        id, user_id, task_id, phase, state, started_at, ends_at, completed_at, duration_minutes, note,
        task_update, is_deleted, hlc_map, server_version, server_updated_at, created_at
      ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16)
    `,
    [
      canonical.id,
      userId,
      canonical.task_id ?? null,
      canonical.phase,
      canonical.state,
      canonical.started_at,
      canonical.ends_at,
      canonical.completed_at ?? null,
      canonical.duration_minutes,
      canonical.note ?? null,
      canonical.task_update ?? "NONE",
      canonical.is_deleted ?? false,
      JSON.stringify(canonical.hlc_map ?? {}),
      canonical.server_version ?? 0,
      canonical.server_updated_at ?? now,
      canonical.created_at ?? now,
    ],
  );
  return canonical;
}

async function mergeExistingPomodoroSession(
  db: DbClient,
  userId: string,
  existing: SyncPomodoroSession,
  incoming: SyncPomodoroSession,
): Promise<SyncPomodoroSession> {
  let merged = cloneSession(existing);
  const accepted: string[] = [];
  const rejected: string[] = [];
  for (const field of dedupe(incoming.dirty_fields ?? [])) {
    const incomingHlc = incoming.hlc_map?.[field];
    const existingHlc = existing.hlc_map?.[field];
    if (!POMODORO_FIELDS.has(field) || !shouldAcceptIncoming(incomingHlc, existingHlc)) {
      rejected.push(field);
      continue;
    }
    switch (field) {
      case "task":
        merged.task_id = incoming.task_id ?? undefined;
        break;
      case "phase":
        merged.phase = incoming.phase;
        break;
      case "timing":
        merged.phase = incoming.phase;
        merged.started_at = incoming.started_at;
        merged.ends_at = incoming.ends_at;
        merged.duration_minutes = incoming.duration_minutes;
        break;
      case "state":
        merged.state = incoming.state;
        merged.completed_at = incoming.completed_at ?? undefined;
        break;
      case "started_at":
        merged.started_at = incoming.started_at;
        break;
      case "ends_at":
        merged.ends_at = incoming.ends_at;
        break;
      case "completed_at":
        merged.completed_at = incoming.completed_at ?? undefined;
        break;
      case "duration_minutes":
        merged.duration_minutes = incoming.duration_minutes;
        break;
      case "note":
        merged.note = incoming.note ?? undefined;
        break;
      case "task_update":
      case "outcome":
        merged.task_update = incoming.task_update ?? "NONE";
        break;
      case "delete":
        merged.is_deleted = incoming.is_deleted ?? false;
        break;
    }
    merged.hlc_map = { ...(merged.hlc_map ?? {}), [field]: incomingHlc! };
    accepted.push(field);
  }
  if (accepted.length === 0) {
    return { ...merged, accepted_fields: [], rejected_fields: rejected };
  }
  const updated: SyncPomodoroSession = {
    ...merged,
    accepted_fields: accepted,
    rejected_fields: rejected,
    server_version: await nextServerVersion(db),
    server_updated_at: new Date().toISOString(),
    created_at: existing.created_at ?? new Date().toISOString(),
  };
  await db.query(
    `
      update pomodoro_session
      set task_id = $1, phase = $2, state = $3, started_at = $4, ends_at = $5, completed_at = $6, duration_minutes = $7,
          note = $8, task_update = $9, is_deleted = $10, hlc_map = $11, server_version = $12, server_updated_at = $13
      where user_id = $14 and id = $15
    `,
    [
      updated.task_id ?? null,
      updated.phase,
      updated.state,
      updated.started_at,
      updated.ends_at,
      updated.completed_at ?? null,
      updated.duration_minutes,
      updated.note ?? null,
      updated.task_update ?? "NONE",
      updated.is_deleted ?? false,
      JSON.stringify(updated.hlc_map ?? {}),
      updated.server_version ?? 0,
      updated.server_updated_at ?? new Date().toISOString(),
      userId,
      updated.id,
    ],
  );
  return updated;
}

function rowToSyncPomodoroSession(row: Record<string, unknown>): SyncPomodoroSession {
  return cloneSession({
    id: String(row.id),
    task_id: stringOrUndefined(row.task_id),
    phase: String(row.phase),
    state: String(row.state),
    started_at: String(row.started_at),
    ends_at: String(row.ends_at),
    completed_at: stringOrUndefined(row.completed_at),
    duration_minutes: Number(row.duration_minutes),
    note: stringOrUndefined(row.note),
    task_update: stringOrUndefined(row.task_update) ?? "NONE",
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

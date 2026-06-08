import type { DbClient } from "../db/transaction";
import type {
  MutableSyncService,
  PullResponse,
} from "./sync-service-types";
import type { TaskSyncServiceLike } from "./task-sync-service";
import {
  dedupe,
  shouldAcceptIncoming,
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
  "state",
  "started_at",
  "ends_at",
  "completed_at",
  "duration_minutes",
  "note",
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

export function resolvePomodoroSessionSyncService(
  env: Record<string, unknown>,
  tasks: TaskSyncServiceLike,
  _dbClient?: DbClient,
): PomodoroSessionSyncServiceLike {
  const injected = env.__pomodoroSessionSyncService;
  if (isPomodoroSessionSyncServiceLike(injected)) {
    return injected;
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

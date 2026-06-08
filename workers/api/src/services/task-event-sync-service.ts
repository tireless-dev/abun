import type { DbClient } from "../db/transaction";
import { withTransaction } from "../db/transaction";
import type {
  MutableSyncService,
  PullResponse,
} from "./sync-service-types";
import type { TaskSyncServiceLike } from "./task-sync-service";
import {
  nextServerVersion,
  stringOrUndefined,
} from "./sync-utils";

export interface SyncTaskEvent {
  id: string;
  task_id: string;
  journal_date: string;
  event_type: string;
  content?: string | null;
  postponed?: {
    previous_start_not_before?: string | null;
    new_start_not_before?: string | null;
    previous_end_not_after?: string | null;
    new_end_not_after?: string | null;
  } | null;
  event_time: string;
  is_deleted?: boolean;
  accepted?: boolean | null;
  server_version?: number;
  server_updated_at?: string | null;
  created_at?: string | null;
}

export interface TaskEventSyncServiceLike extends MutableSyncService<SyncTaskEvent> {}

export class TaskEventSyncService implements TaskEventSyncServiceLike {
  private nextVersionValue = 0;
  private readonly eventsByUser = new Map<string, Map<string, SyncTaskEvent>>();

  constructor(private readonly tasks: TaskSyncServiceLike) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncTaskEvent>> {
    const items = Array.from(this.userEvents(userId).values())
      .filter((event) => (event.server_version ?? 0) > cursor)
      .sort((left, right) => (left.server_version ?? 0) - (right.server_version ?? 0))
      .slice(0, limit);

    return {
      items: items.map(cloneTaskEvent),
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncTaskEvent[]): Promise<SyncTaskEvent[]> {
    const results: SyncTaskEvent[] = [];

    for (const incoming of items) {
      if (!(await this.tasks.exists(userId, incoming.task_id))) {
        throw new Error("task_id does not belong to current user");
      }

      const events = this.userEvents(userId);
      const existing = events.get(incoming.id);

      if (existing) {
        results.push({
          ...cloneTaskEvent(existing),
          accepted: false,
        });
        continue;
      }

      const now = new Date().toISOString();
      const inserted: SyncTaskEvent = {
        ...cloneTaskEvent(incoming),
        accepted: true,
        server_version: this.nextVersion(),
        server_updated_at: now,
        created_at: incoming.created_at ?? now,
      };
      events.set(inserted.id, inserted);
      results.push(cloneTaskEvent(inserted));
    }

    return results;
  }

  private nextVersion(): number {
    this.nextVersionValue += 1;
    return this.nextVersionValue;
  }

  private userEvents(userId: string): Map<string, SyncTaskEvent> {
    let events = this.eventsByUser.get(userId);
    if (!events) {
      events = new Map();
      this.eventsByUser.set(userId, events);
    }
    return events;
  }
}

let defaultTaskEventSyncService: TaskEventSyncServiceLike | null = null;

class DatabaseTaskEventSyncService implements TaskEventSyncServiceLike {
  constructor(
    private readonly db: DbClient,
    private readonly tasks: TaskSyncServiceLike,
  ) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncTaskEvent>> {
    const result = await this.db.query(
      `
        select * from task_event
        where user_id = $1 and server_version > $2
        order by server_version asc
        limit $3
      `,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToSyncTaskEvent);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncTaskEvent[]): Promise<SyncTaskEvent[]> {
    const results: SyncTaskEvent[] = [];
    for (const incoming of items) {
      const result = await withTransaction(this.db, async (tx) => {
        if (!(await this.tasks.exists(userId, incoming.task_id))) {
          throw new Error("task_id does not belong to current user");
        }
        const existing = await selectOneTaskEvent(tx, userId, incoming.id);
        if (existing) return { ...existing, accepted: false };
        return insertNewTaskEvent(tx, userId, incoming);
      });
      results.push(result);
    }
    return results;
  }
}

export function resolveTaskEventSyncService(
  env: Record<string, unknown>,
  tasks: TaskSyncServiceLike,
  dbClient?: DbClient,
): TaskEventSyncServiceLike {
  const injected = env.__taskEventSyncService;
  if (isTaskEventSyncServiceLike(injected)) {
    return injected;
  }

  if (dbClient) {
    return new DatabaseTaskEventSyncService(dbClient, tasks);
  }

  defaultTaskEventSyncService ??= new TaskEventSyncService(tasks);
  return defaultTaskEventSyncService;
}

function isTaskEventSyncServiceLike(value: unknown): value is TaskEventSyncServiceLike {
  return (
    typeof value === "object" &&
    value !== null &&
    "list" in value &&
    typeof value.list === "function" &&
    "push" in value &&
    typeof value.push === "function"
  );
}

function cloneTaskEvent(event: SyncTaskEvent): SyncTaskEvent {
  return {
    ...event,
    content: event.content ?? undefined,
    postponed: event.postponed ? { ...event.postponed } : undefined,
    is_deleted: event.is_deleted ?? false,
    accepted: event.accepted ?? undefined,
    server_updated_at: event.server_updated_at ?? undefined,
    created_at: event.created_at ?? undefined,
  };
}

async function selectOneTaskEvent(db: DbClient, userId: string, id: string): Promise<SyncTaskEvent | null> {
  const result = await db.query(
    `select * from task_event where user_id = $1 and id = $2`,
    [userId, id],
  );
  const row = result.rows[0];
  return row ? rowToSyncTaskEvent(row) : null;
}

async function insertNewTaskEvent(db: DbClient, userId: string, incoming: SyncTaskEvent): Promise<SyncTaskEvent> {
  const now = new Date().toISOString();
  const canonical: SyncTaskEvent = {
    ...cloneTaskEvent(incoming),
    accepted: true,
    server_version: await nextServerVersion(db),
    server_updated_at: now,
    created_at: incoming.created_at ?? now,
  };
  await db.query(
    `
      insert into task_event(
        id, user_id, task_id, journal_date, event_type, content, postponed_json, event_time, is_deleted, server_version, server_updated_at, created_at
      ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
    `,
    [
      canonical.id,
      userId,
      canonical.task_id,
      canonical.journal_date,
      canonical.event_type,
      canonical.content ?? null,
      canonical.postponed ? JSON.stringify(canonical.postponed) : null,
      canonical.event_time,
      canonical.is_deleted ?? false,
      canonical.server_version ?? 0,
      canonical.server_updated_at ?? now,
      canonical.created_at ?? now,
    ],
  );
  return canonical;
}

function rowToSyncTaskEvent(row: Record<string, unknown>): SyncTaskEvent {
  return cloneTaskEvent({
    id: String(row.id),
    task_id: String(row.task_id),
    journal_date: String(row.journal_date),
    event_type: String(row.event_type),
    content: stringOrUndefined(row.content),
    postponed: typeof row.postponed_json === "string" && row.postponed_json.length > 0
      ? JSON.parse(row.postponed_json)
      : undefined,
    event_time: String(row.event_time),
    is_deleted: Boolean(row.is_deleted),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

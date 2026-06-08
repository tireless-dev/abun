import type { DbClient } from "../db/transaction";
import type {
  MutableSyncService,
  PullResponse,
} from "./sync-service-types";
import type { TaskSyncServiceLike } from "./task-sync-service";

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

export function resolveTaskEventSyncService(
  env: Record<string, unknown>,
  tasks: TaskSyncServiceLike,
  _dbClient?: DbClient,
): TaskEventSyncServiceLike {
  const injected = env.__taskEventSyncService;
  if (isTaskEventSyncServiceLike(injected)) {
    return injected;
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

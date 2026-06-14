import type { DbClient } from "../db/transaction";
import { withTransaction } from "../db/transaction";
import type {
  MutableSyncService,
  PullResponse,
} from "./sync-service-types";
import {
  dedupe,
  hasOwnedRecord,
  nextServerVersion,
  parseStringMap,
  shouldAcceptIncoming,
  stringOrUndefined,
} from "./sync-utils";

export interface SyncTask {
  id: string;
  parent_id?: string | null;
  routine_id?: string | null;
  title: string;
  detail?: string | null;
  start_not_before?: string | null;
  end_not_after?: string | null;
  estimated_duration?: string | null;
  is_deleted?: boolean;
  hlc_map?: Record<string, string>;
  dirty_fields?: string[];
  accepted_fields?: string[] | null;
  rejected_fields?: string[] | null;
  server_version?: number;
  server_updated_at?: string | null;
  created_at?: string | null;
}

export interface TaskSyncServiceLike extends MutableSyncService<SyncTask> {
  exists(userId: string, id: string): Promise<boolean> | boolean;
}

const TASK_FIELDS = new Set([
  "title",
  "parent",
  "routine",
  "detail",
  "start_not_before",
  "end_not_after",
  "estimated_duration",
  "delete",
]);

export class TaskSyncService implements TaskSyncServiceLike {
  private nextVersionValue = 0;
  private readonly tasksByUser = new Map<string, Map<string, SyncTask>>();

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncTask>> {
    const tasks = Array.from(this.userTasks(userId).values())
      .filter((task) => (task.server_version ?? 0) > cursor)
      .sort((left, right) => (left.server_version ?? 0) - (right.server_version ?? 0))
      .slice(0, limit);

    return {
      items: tasks.map(cloneTask),
      next_cursor: tasks.at(-1)?.server_version ?? cursor,
      has_more: tasks.length === limit,
    };
  }

  async push(userId: string, items: SyncTask[]): Promise<SyncTask[]> {
    return items.map((incoming) => {
      const tasks = this.userTasks(userId);
      const existing = tasks.get(incoming.id);

      if (!existing) {
        const inserted = this.insertNew(incoming);
        tasks.set(inserted.id, inserted);
        return cloneTask(inserted);
      }

      const merged = this.mergeExisting(existing, incoming);
      tasks.set(merged.id, merged);
      return cloneTask(merged);
    });
  }

  async exists(userId: string, id: string): Promise<boolean> {
    return this.userTasks(userId).has(id);
  }

  private insertNew(incoming: SyncTask): SyncTask {
    const now = new Date().toISOString();
    return {
      ...cloneTask(incoming),
      is_deleted: incoming.is_deleted ?? false,
      hlc_map: { ...(incoming.hlc_map ?? {}) },
      dirty_fields: [...(incoming.dirty_fields ?? [])],
      accepted_fields: dedupe(incoming.dirty_fields ?? []),
      rejected_fields: [],
      server_version: this.nextVersion(),
      server_updated_at: now,
      created_at: incoming.created_at ?? now,
    };
  }

  private mergeExisting(existing: SyncTask, incoming: SyncTask): SyncTask {
    let merged = cloneTask(existing);
    const accepted: string[] = [];
    const rejected: string[] = [];
    const dirtyFields = dedupe(incoming.dirty_fields ?? []);

    for (const field of dirtyFields) {
      if (!TASK_FIELDS.has(field)) {
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
        case "title":
          merged.title = incoming.title;
          break;
        case "parent":
          merged.parent_id = incoming.parent_id ?? undefined;
          break;
        case "routine":
          merged.routine_id = incoming.routine_id ?? undefined;
          break;
        case "detail":
          merged.detail = incoming.detail ?? undefined;
          break;
        case "start_not_before":
          merged.start_not_before = incoming.start_not_before ?? undefined;
          break;
        case "end_not_after":
          merged.end_not_after = incoming.end_not_after ?? undefined;
          break;
        case "estimated_duration":
          merged.estimated_duration = incoming.estimated_duration ?? undefined;
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

  private userTasks(userId: string): Map<string, SyncTask> {
    let tasks = this.tasksByUser.get(userId);
    if (!tasks) {
      tasks = new Map();
      this.tasksByUser.set(userId, tasks);
    }
    return tasks;
  }
}

export class DatabaseTaskSyncService implements TaskSyncServiceLike {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncTask>> {
    const result = await this.db.query(
      `
        select * from task
        where user_id = $1 and server_version > $2
        order by server_version asc
        limit $3
      `,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToSyncTask);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncTask[]): Promise<SyncTask[]> {
    const results: SyncTask[] = [];

    for (const incoming of items) {
      const result = await withTransaction(this.db, async (tx) => {
        await validateTaskOwnership(tx, userId, incoming.parent_id, incoming.routine_id);
        const existing = await selectOneTask(tx, userId, incoming.id);
        if (!existing) {
          return insertNewTask(tx, userId, incoming);
        }
        return mergeExistingTask(tx, userId, existing, incoming);
      });
      results.push(result);
    }

    return results;
  }

  async exists(userId: string, id: string): Promise<boolean> {
    return hasOwnedRecord(this.db, "task", userId, id);
  }
}

let defaultTaskSyncService: TaskSyncServiceLike | null = null;

export function resolveTaskSyncService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): TaskSyncServiceLike {
  const injected = env.__taskSyncService;
  if (isTaskSyncServiceLike(injected)) {
    return injected;
  }

  if (dbClient) {
    return new DatabaseTaskSyncService(dbClient);
  }

  defaultTaskSyncService ??= new TaskSyncService();
  return defaultTaskSyncService;
}

function isTaskSyncServiceLike(value: unknown): value is TaskSyncServiceLike {
  return (
    typeof value === "object" &&
    value !== null &&
    "list" in value &&
    typeof value.list === "function" &&
    "push" in value &&
    typeof value.push === "function"
  );
}

function cloneTask(task: SyncTask): SyncTask {
  return {
    ...task,
    parent_id: task.parent_id ?? undefined,
    routine_id: task.routine_id ?? undefined,
    detail: task.detail ?? undefined,
    start_not_before: task.start_not_before ?? undefined,
    end_not_after: task.end_not_after ?? undefined,
    estimated_duration: task.estimated_duration ?? undefined,
    hlc_map: { ...(task.hlc_map ?? {}) },
    dirty_fields: [...(task.dirty_fields ?? [])],
    accepted_fields: task.accepted_fields ? [...task.accepted_fields] : undefined,
    rejected_fields: task.rejected_fields ? [...task.rejected_fields] : undefined,
    server_updated_at: task.server_updated_at ?? undefined,
    created_at: task.created_at ?? undefined,
  };
}

async function validateTaskOwnership(
  db: DbClient,
  userId: string,
  parentId?: string | null,
  routineId?: string | null,
): Promise<void> {
  if (parentId) {
    const parent = await db.query(
      `select 1 from task where user_id = $1 and id = $2`,
      [userId, parentId],
    );
    if (parent.rows.length === 0) {
      throw new Error("parent_id does not belong to current user");
    }
  }

  if (routineId) {
    const routine = await db.query(
      `select 1 from routine where user_id = $1 and id = $2`,
      [userId, routineId],
    );
    if (routine.rows.length === 0) {
      throw new Error("routine_id does not belong to current user");
    }
  }
}

async function selectOneTask(
  db: DbClient,
  userId: string,
  id: string,
): Promise<SyncTask | null> {
  const result = await db.query(
    `select * from task where user_id = $1 and id = $2`,
    [userId, id],
  );
  const row = result.rows[0];
  return row ? rowToSyncTask(row) : null;
}

async function insertNewTask(
  db: DbClient,
  userId: string,
  incoming: SyncTask,
): Promise<SyncTask> {
  const now = new Date().toISOString();
  const canonical: SyncTask = {
    ...cloneTask(incoming),
    is_deleted: incoming.is_deleted ?? false,
    accepted_fields: dedupe(incoming.dirty_fields ?? []),
    rejected_fields: [],
    server_version: await nextServerVersion(db),
    server_updated_at: now,
    created_at: incoming.created_at ?? now,
  };

  await db.query(
    `
      insert into task(
        id, user_id, parent_id, routine_id, title, detail, start_not_before, end_not_after,
        estimated_duration, is_deleted, hlc_map, server_version, server_updated_at, created_at
      ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
    `,
    [
      canonical.id,
      userId,
      canonical.parent_id ?? null,
      canonical.routine_id ?? null,
      canonical.title,
      canonical.detail ?? null,
      canonical.start_not_before ?? null,
      canonical.end_not_after ?? null,
      canonical.estimated_duration ?? null,
      canonical.is_deleted ?? false,
      JSON.stringify(canonical.hlc_map ?? {}),
      canonical.server_version ?? 0,
      canonical.server_updated_at ?? now,
      canonical.created_at ?? now,
    ],
  );

  return canonical;
}

async function mergeExistingTask(
  db: DbClient,
  userId: string,
  existing: SyncTask,
  incoming: SyncTask,
): Promise<SyncTask> {
  let merged = cloneTask(existing);
  const accepted: string[] = [];
  const rejected: string[] = [];

  for (const field of dedupe(incoming.dirty_fields ?? [])) {
    if (!TASK_FIELDS.has(field)) {
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
      case "title":
        merged.title = incoming.title;
        break;
      case "parent":
        merged.parent_id = incoming.parent_id ?? undefined;
        break;
      case "routine":
        merged.routine_id = incoming.routine_id ?? undefined;
        break;
      case "detail":
        merged.detail = incoming.detail ?? undefined;
        break;
      case "start_not_before":
        merged.start_not_before = incoming.start_not_before ?? undefined;
        break;
      case "end_not_after":
        merged.end_not_after = incoming.end_not_after ?? undefined;
        break;
      case "estimated_duration":
        merged.estimated_duration = incoming.estimated_duration ?? undefined;
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

  const updated: SyncTask = {
    ...merged,
    accepted_fields: accepted,
    rejected_fields: rejected,
    server_version: await nextServerVersion(db),
    server_updated_at: new Date().toISOString(),
    created_at: existing.created_at ?? new Date().toISOString(),
  };

  await db.query(
    `
      update task
      set parent_id = $1, routine_id = $2, title = $3, detail = $4, start_not_before = $5,
          end_not_after = $6, estimated_duration = $7, is_deleted = $8, hlc_map = $9,
          server_version = $10, server_updated_at = $11
      where user_id = $12 and id = $13
    `,
    [
      updated.parent_id ?? null,
      updated.routine_id ?? null,
      updated.title,
      updated.detail ?? null,
      updated.start_not_before ?? null,
      updated.end_not_after ?? null,
      updated.estimated_duration ?? null,
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

function rowToSyncTask(row: Record<string, unknown>): SyncTask {
  return cloneTask({
    id: String(row.id),
    parent_id: stringOrUndefined(row.parent_id),
    routine_id: stringOrUndefined(row.routine_id),
    title: String(row.title),
    detail: stringOrUndefined(row.detail),
    start_not_before: stringOrUndefined(row.start_not_before),
    end_not_after: stringOrUndefined(row.end_not_after),
    estimated_duration: stringOrUndefined(row.estimated_duration),
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

import type { DbClient } from "../db/transaction";
import { withTransaction } from "../db/transaction";
import type {
  MutableSyncService,
  PullResponse,
} from "./sync-service-types";
import {
  dedupe,
  nextServerVersion,
  parseStringMap,
  shouldAcceptIncoming,
  stringOrUndefined,
} from "./sync-utils";

export interface SyncRoutine {
  id: string;
  template_title: string;
  template_detail?: string | null;
  recurrence_rule: string;
  default_start_not_before?: string | null;
  default_estimated_duration?: string | null;
  is_active?: boolean;
  is_deleted?: boolean;
  hlc_map?: Record<string, string>;
  dirty_fields?: string[];
  accepted_fields?: string[] | null;
  rejected_fields?: string[] | null;
  server_version?: number;
  server_updated_at?: string | null;
  created_at?: string | null;
}

export interface RoutineSyncServiceLike extends MutableSyncService<SyncRoutine> {}

const ROUTINE_FIELDS = new Set([
  "template_title",
  "template_detail",
  "recurrence_rule",
  "default_start_not_before",
  "default_estimated_duration",
  "active",
  "delete",
]);

export class RoutineSyncService implements RoutineSyncServiceLike {
  private nextVersionValue = 0;
  private readonly routinesByUser = new Map<string, Map<string, SyncRoutine>>();

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncRoutine>> {
    const items = Array.from(this.userRoutines(userId).values())
      .filter((routine) => (routine.server_version ?? 0) > cursor)
      .sort((left, right) => (left.server_version ?? 0) - (right.server_version ?? 0))
      .slice(0, limit);

    return {
      items: items.map(cloneRoutine),
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncRoutine[]): Promise<SyncRoutine[]> {
    return items.map((incoming) => {
      const routines = this.userRoutines(userId);
      const existing = routines.get(incoming.id);

      if (!existing) {
        const inserted = this.insertNew(incoming);
        routines.set(inserted.id, inserted);
        return cloneRoutine(inserted);
      }

      const merged = this.mergeExisting(existing, incoming);
      routines.set(merged.id, merged);
      return cloneRoutine(merged);
    });
  }

  private insertNew(incoming: SyncRoutine): SyncRoutine {
    const now = new Date().toISOString();
    return {
      ...cloneRoutine(incoming),
      is_active: incoming.is_active ?? true,
      is_deleted: incoming.is_deleted ?? false,
      accepted_fields: dedupe(incoming.dirty_fields ?? []),
      rejected_fields: [],
      server_version: this.nextVersion(),
      server_updated_at: now,
      created_at: incoming.created_at ?? now,
    };
  }

  private mergeExisting(existing: SyncRoutine, incoming: SyncRoutine): SyncRoutine {
    let merged = cloneRoutine(existing);
    const accepted: string[] = [];
    const rejected: string[] = [];

    for (const field of dedupe(incoming.dirty_fields ?? [])) {
      if (!ROUTINE_FIELDS.has(field)) {
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
        case "template_title":
          merged.template_title = incoming.template_title;
          break;
        case "template_detail":
          merged.template_detail = incoming.template_detail ?? undefined;
          break;
        case "recurrence_rule":
          merged.recurrence_rule = incoming.recurrence_rule;
          break;
        case "default_start_not_before":
          merged.default_start_not_before = incoming.default_start_not_before ?? undefined;
          break;
        case "default_estimated_duration":
          merged.default_estimated_duration = incoming.default_estimated_duration ?? undefined;
          break;
        case "active":
          merged.is_active = incoming.is_active ?? true;
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

  private userRoutines(userId: string): Map<string, SyncRoutine> {
    let routines = this.routinesByUser.get(userId);
    if (!routines) {
      routines = new Map();
      this.routinesByUser.set(userId, routines);
    }
    return routines;
  }
}

let defaultRoutineSyncService: RoutineSyncServiceLike | null = null;

class DatabaseRoutineSyncService implements RoutineSyncServiceLike {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncRoutine>> {
    const result = await this.db.query(
      `
        select * from routine
        where user_id = $1 and server_version > $2
        order by server_version asc
        limit $3
      `,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToSyncRoutine);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncRoutine[]): Promise<SyncRoutine[]> {
    const results: SyncRoutine[] = [];
    for (const incoming of items) {
      const result = await withTransaction(this.db, async (tx) => {
        const existing = await selectOneRoutine(tx, userId, incoming.id);
        if (!existing) return insertNewRoutine(tx, userId, incoming);
        return mergeExistingRoutine(tx, userId, existing, incoming);
      });
      results.push(result);
    }
    return results;
  }
}

export function resolveRoutineSyncService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): RoutineSyncServiceLike {
  const injected = env.__routineSyncService;
  if (isRoutineSyncServiceLike(injected)) {
    return injected;
  }

  if (dbClient) {
    return new DatabaseRoutineSyncService(dbClient);
  }

  defaultRoutineSyncService ??= new RoutineSyncService();
  return defaultRoutineSyncService;
}

function isRoutineSyncServiceLike(value: unknown): value is RoutineSyncServiceLike {
  return (
    typeof value === "object" &&
    value !== null &&
    "list" in value &&
    typeof value.list === "function" &&
    "push" in value &&
    typeof value.push === "function"
  );
}

function cloneRoutine(routine: SyncRoutine): SyncRoutine {
  return {
    ...routine,
    template_detail: routine.template_detail ?? undefined,
    default_start_not_before: routine.default_start_not_before ?? undefined,
    default_estimated_duration: routine.default_estimated_duration ?? undefined,
    is_active: routine.is_active ?? true,
    is_deleted: routine.is_deleted ?? false,
    hlc_map: { ...(routine.hlc_map ?? {}) },
    dirty_fields: [...(routine.dirty_fields ?? [])],
    accepted_fields: routine.accepted_fields ? [...routine.accepted_fields] : undefined,
    rejected_fields: routine.rejected_fields ? [...routine.rejected_fields] : undefined,
    server_updated_at: routine.server_updated_at ?? undefined,
    created_at: routine.created_at ?? undefined,
  };
}

async function selectOneRoutine(db: DbClient, userId: string, id: string): Promise<SyncRoutine | null> {
  const result = await db.query(
    `select * from routine where user_id = $1 and id = $2`,
    [userId, id],
  );
  const row = result.rows[0];
  return row ? rowToSyncRoutine(row) : null;
}

async function insertNewRoutine(db: DbClient, userId: string, incoming: SyncRoutine): Promise<SyncRoutine> {
  const now = new Date().toISOString();
  const canonical: SyncRoutine = {
    ...cloneRoutine(incoming),
    is_active: incoming.is_active ?? true,
    is_deleted: incoming.is_deleted ?? false,
    accepted_fields: dedupe(incoming.dirty_fields ?? []),
    rejected_fields: [],
    server_version: await nextServerVersion(db),
    server_updated_at: now,
    created_at: incoming.created_at ?? now,
  };
  await db.query(
    `
      insert into routine(
        id, user_id, template_title, template_detail, recurrence_rule, default_start_not_before,
        default_estimated_duration, is_active, is_deleted, hlc_map, server_version, server_updated_at, created_at
      ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
    `,
    [
      canonical.id,
      userId,
      canonical.template_title,
      canonical.template_detail ?? null,
      canonical.recurrence_rule,
      canonical.default_start_not_before ?? null,
      canonical.default_estimated_duration ?? null,
      canonical.is_active ?? true,
      canonical.is_deleted ?? false,
      JSON.stringify(canonical.hlc_map ?? {}),
      canonical.server_version ?? 0,
      canonical.server_updated_at ?? now,
      canonical.created_at ?? now,
    ],
  );
  return canonical;
}

async function mergeExistingRoutine(
  db: DbClient,
  userId: string,
  existing: SyncRoutine,
  incoming: SyncRoutine,
): Promise<SyncRoutine> {
  let merged = cloneRoutine(existing);
  const accepted: string[] = [];
  const rejected: string[] = [];

  for (const field of dedupe(incoming.dirty_fields ?? [])) {
    const incomingHlc = incoming.hlc_map?.[field];
    const existingHlc = existing.hlc_map?.[field];
    if (!ROUTINE_FIELDS.has(field) || !shouldAcceptIncoming(incomingHlc, existingHlc)) {
      rejected.push(field);
      continue;
    }
    switch (field) {
      case "template_title":
        merged.template_title = incoming.template_title;
        break;
      case "template_detail":
        merged.template_detail = incoming.template_detail ?? undefined;
        break;
      case "recurrence_rule":
        merged.recurrence_rule = incoming.recurrence_rule;
        break;
      case "default_start_not_before":
        merged.default_start_not_before = incoming.default_start_not_before ?? undefined;
        break;
      case "default_estimated_duration":
        merged.default_estimated_duration = incoming.default_estimated_duration ?? undefined;
        break;
      case "active":
        merged.is_active = incoming.is_active ?? true;
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
  const updated: SyncRoutine = {
    ...merged,
    accepted_fields: accepted,
    rejected_fields: rejected,
    server_version: await nextServerVersion(db),
    server_updated_at: new Date().toISOString(),
    created_at: existing.created_at ?? new Date().toISOString(),
  };
  await db.query(
    `
      update routine
      set template_title = $1, template_detail = $2, recurrence_rule = $3, default_start_not_before = $4,
          default_estimated_duration = $5, is_active = $6, is_deleted = $7, hlc_map = $8, server_version = $9, server_updated_at = $10
      where user_id = $11 and id = $12
    `,
    [
      updated.template_title,
      updated.template_detail ?? null,
      updated.recurrence_rule,
      updated.default_start_not_before ?? null,
      updated.default_estimated_duration ?? null,
      updated.is_active ?? true,
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

function rowToSyncRoutine(row: Record<string, unknown>): SyncRoutine {
  return cloneRoutine({
    id: String(row.id),
    template_title: String(row.template_title),
    template_detail: stringOrUndefined(row.template_detail),
    recurrence_rule: stringOrUndefined(row.recurrence_rule) ?? stringOrUndefined(row.cron_schedule) ?? "",
    default_start_not_before: stringOrUndefined(row.default_start_not_before),
    default_estimated_duration: stringOrUndefined(row.default_estimated_duration),
    is_active: Boolean(row.is_active),
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

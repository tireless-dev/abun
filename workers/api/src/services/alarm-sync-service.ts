import type { DbClient } from "../db/transaction";
import { withTransaction } from "../db/transaction";
import type {
  MutableSyncService,
  PullResponse,
} from "./sync-service-types";
import type { TaskSyncServiceLike } from "./task-sync-service";
import {
  dedupe,
  hasOwnedRecord,
  nextServerVersion,
  parseStringMap,
  shouldAcceptIncoming,
  stringOrUndefined,
} from "./sync-utils";

export interface SyncAlarm {
  id: string;
  task_id: string;
  trigger_time: string;
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

export interface AlarmSyncServiceLike extends MutableSyncService<SyncAlarm> {}

const ALARM_FIELDS = new Set(["trigger_time", "trigger", "active", "delete"]);

export class AlarmSyncService implements AlarmSyncServiceLike {
  private nextVersionValue = 0;
  private readonly alarmsByUser = new Map<string, Map<string, SyncAlarm>>();

  constructor(private readonly tasks: TaskSyncServiceLike) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncAlarm>> {
    const items = Array.from(this.userAlarms(userId).values())
      .filter((alarm) => (alarm.server_version ?? 0) > cursor)
      .sort((left, right) => (left.server_version ?? 0) - (right.server_version ?? 0))
      .slice(0, limit);

    return {
      items: items.map(cloneAlarm),
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncAlarm[]): Promise<SyncAlarm[]> {
    const results: SyncAlarm[] = [];

    for (const incoming of items) {
      if (!(await this.tasks.exists(userId, incoming.task_id))) {
        throw new Error("task_id does not belong to current user");
      }

      const alarms = this.userAlarms(userId);
      const existing = alarms.get(incoming.id);
      if (!existing) {
        const inserted = this.insertNew(incoming);
        alarms.set(inserted.id, inserted);
        results.push(cloneAlarm(inserted));
        continue;
      }

      const merged = this.mergeExisting(existing, incoming);
      alarms.set(merged.id, merged);
      results.push(cloneAlarm(merged));
    }

    return results;
  }

  private insertNew(incoming: SyncAlarm): SyncAlarm {
    const now = new Date().toISOString();
    return {
      ...cloneAlarm(incoming),
      is_active: incoming.is_active ?? true,
      is_deleted: incoming.is_deleted ?? false,
      accepted_fields: dedupe(incoming.dirty_fields ?? []),
      rejected_fields: [],
      server_version: this.nextVersion(),
      server_updated_at: now,
      created_at: incoming.created_at ?? now,
    };
  }

  private mergeExisting(existing: SyncAlarm, incoming: SyncAlarm): SyncAlarm {
    let merged = cloneAlarm(existing);
    const accepted: string[] = [];
    const rejected: string[] = [];

    for (const field of dedupe(incoming.dirty_fields ?? [])) {
      if (!ALARM_FIELDS.has(field)) {
        rejected.push(field);
        continue;
      }

      const incomingHlc = incoming.hlc_map?.[field];
      const existingHlc = existing.hlc_map?.[field];
      if (!shouldAcceptIncoming(incomingHlc, existingHlc)) {
        rejected.push(field);
        continue;
      }

      if (field === "trigger_time" || field === "trigger") {
        merged.trigger_time = incoming.trigger_time;
      } else if (field === "active") {
        merged.is_active = incoming.is_active ?? true;
      } else if (field === "delete") {
        merged.is_deleted = incoming.is_deleted ?? false;
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

  private userAlarms(userId: string): Map<string, SyncAlarm> {
    let alarms = this.alarmsByUser.get(userId);
    if (!alarms) {
      alarms = new Map();
      this.alarmsByUser.set(userId, alarms);
    }
    return alarms;
  }
}

let defaultAlarmSyncService: AlarmSyncServiceLike | null = null;

class DatabaseAlarmSyncService implements AlarmSyncServiceLike {
  constructor(
    private readonly db: DbClient,
    private readonly tasks: TaskSyncServiceLike,
  ) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncAlarm>> {
    const result = await this.db.query(
      `
        select * from alarm
        where user_id = $1 and server_version > $2
        order by server_version asc
        limit $3
      `,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToSyncAlarm);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncAlarm[]): Promise<SyncAlarm[]> {
    const results: SyncAlarm[] = [];
    for (const incoming of items) {
      const result = await withTransaction(this.db, async (tx) => {
        if (!(await this.tasks.exists(userId, incoming.task_id))) {
          throw new Error("task_id does not belong to current user");
        }
        const existing = await selectOneAlarm(tx, userId, incoming.id);
        if (!existing) return insertNewAlarm(tx, userId, incoming);
        return mergeExistingAlarm(tx, userId, existing, incoming);
      });
      results.push(result);
    }
    return results;
  }
}

export function resolveAlarmSyncService(
  env: Record<string, unknown>,
  tasks: TaskSyncServiceLike,
  dbClient?: DbClient,
): AlarmSyncServiceLike {
  const injected = env.__alarmSyncService;
  if (isAlarmSyncServiceLike(injected)) {
    return injected;
  }

  if (dbClient) {
    return new DatabaseAlarmSyncService(dbClient, tasks);
  }

  defaultAlarmSyncService ??= new AlarmSyncService(tasks);
  return defaultAlarmSyncService;
}

function isAlarmSyncServiceLike(value: unknown): value is AlarmSyncServiceLike {
  return (
    typeof value === "object" &&
    value !== null &&
    "list" in value &&
    typeof value.list === "function" &&
    "push" in value &&
    typeof value.push === "function"
  );
}

function cloneAlarm(alarm: SyncAlarm): SyncAlarm {
  return {
    ...alarm,
    is_active: alarm.is_active ?? true,
    is_deleted: alarm.is_deleted ?? false,
    hlc_map: { ...(alarm.hlc_map ?? {}) },
    dirty_fields: [...(alarm.dirty_fields ?? [])],
    accepted_fields: alarm.accepted_fields ? [...alarm.accepted_fields] : undefined,
    rejected_fields: alarm.rejected_fields ? [...alarm.rejected_fields] : undefined,
    server_updated_at: alarm.server_updated_at ?? undefined,
    created_at: alarm.created_at ?? undefined,
  };
}

async function selectOneAlarm(db: DbClient, userId: string, id: string): Promise<SyncAlarm | null> {
  const result = await db.query(
    `select * from alarm where user_id = $1 and id = $2`,
    [userId, id],
  );
  const row = result.rows[0];
  return row ? rowToSyncAlarm(row) : null;
}

async function insertNewAlarm(db: DbClient, userId: string, incoming: SyncAlarm): Promise<SyncAlarm> {
  const now = new Date().toISOString();
  const canonical: SyncAlarm = {
    ...cloneAlarm(incoming),
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
      insert into alarm(
        id, user_id, task_id, trigger_time, is_active, is_deleted, hlc_map, server_version, server_updated_at, created_at
      ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
    `,
    [
      canonical.id,
      userId,
      canonical.task_id,
      canonical.trigger_time,
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

async function mergeExistingAlarm(
  db: DbClient,
  userId: string,
  existing: SyncAlarm,
  incoming: SyncAlarm,
): Promise<SyncAlarm> {
  let merged = cloneAlarm(existing);
  const accepted: string[] = [];
  const rejected: string[] = [];
  for (const field of dedupe(incoming.dirty_fields ?? [])) {
    const incomingHlc = incoming.hlc_map?.[field];
    const existingHlc = existing.hlc_map?.[field];
    if (!ALARM_FIELDS.has(field) || !shouldAcceptIncoming(incomingHlc, existingHlc)) {
      rejected.push(field);
      continue;
    }
    if (field === "trigger" || field === "trigger_time") {
      merged.trigger_time = incoming.trigger_time;
    } else if (field === "active") {
      merged.is_active = incoming.is_active ?? true;
    } else {
      merged.is_deleted = incoming.is_deleted ?? false;
    }
    merged.hlc_map = { ...(merged.hlc_map ?? {}), [field]: incomingHlc! };
    accepted.push(field);
  }

  if (accepted.length === 0) {
    return { ...merged, accepted_fields: [], rejected_fields: rejected };
  }
  const updated: SyncAlarm = {
    ...merged,
    accepted_fields: accepted,
    rejected_fields: rejected,
    server_version: await nextServerVersion(db),
    server_updated_at: new Date().toISOString(),
    created_at: existing.created_at ?? new Date().toISOString(),
  };
  await db.query(
    `
      update alarm
      set task_id = $1, trigger_time = $2, is_active = $3, is_deleted = $4, hlc_map = $5, server_version = $6, server_updated_at = $7
      where user_id = $8 and id = $9
    `,
    [
      updated.task_id,
      updated.trigger_time,
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

function rowToSyncAlarm(row: Record<string, unknown>): SyncAlarm {
  return cloneAlarm({
    id: String(row.id),
    task_id: String(row.task_id),
    trigger_time: String(row.trigger_time),
    is_active: Boolean(row.is_active),
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

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

const ALARM_FIELDS = new Set(["trigger_time", "active", "delete"]);

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

      if (field === "trigger_time") {
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

export function resolveAlarmSyncService(
  env: Record<string, unknown>,
  tasks: TaskSyncServiceLike,
  _dbClient?: DbClient,
): AlarmSyncServiceLike {
  const injected = env.__alarmSyncService;
  if (isAlarmSyncServiceLike(injected)) {
    return injected;
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

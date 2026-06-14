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

export interface SyncPreference {
  key: string;
  value?: string | null;
  value_type: string;
  is_deleted?: boolean;
  hlc_map?: Record<string, string>;
  dirty_fields?: string[];
  accepted_fields?: string[] | null;
  rejected_fields?: string[] | null;
  server_version?: number;
  server_updated_at?: string | null;
  created_at?: string | null;
}

export interface PreferenceSyncServiceLike extends MutableSyncService<SyncPreference> {}

const PREFERENCE_FIELDS = new Set(["value", "delete"]);

export class PreferenceSyncService implements PreferenceSyncServiceLike {
  private nextVersionValue = 0;
  private readonly preferencesByUser = new Map<string, Map<string, SyncPreference>>();

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncPreference>> {
    const items = Array.from(this.userPreferences(userId).values())
      .filter((preference) => (preference.server_version ?? 0) > cursor)
      .sort((left, right) => (left.server_version ?? 0) - (right.server_version ?? 0))
      .slice(0, limit);

    return {
      items: items.map(clonePreference),
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncPreference[]): Promise<SyncPreference[]> {
    return items.map((incoming) => {
      const preferences = this.userPreferences(userId);
      const existing = preferences.get(incoming.key);

      if (!existing) {
        const inserted = this.insertNew(incoming);
        preferences.set(inserted.key, inserted);
        return clonePreference(inserted);
      }

      const merged = this.mergeExisting(existing, incoming);
      preferences.set(merged.key, merged);
      return clonePreference(merged);
    });
  }

  private insertNew(incoming: SyncPreference): SyncPreference {
    const now = new Date().toISOString();
    return {
      ...clonePreference(incoming),
      accepted_fields: dedupe(incoming.dirty_fields ?? []),
      rejected_fields: [],
      server_version: this.nextVersion(),
      server_updated_at: now,
      created_at: incoming.created_at ?? now,
    };
  }

  private mergeExisting(existing: SyncPreference, incoming: SyncPreference): SyncPreference {
    let merged = clonePreference(existing);
    const accepted: string[] = [];
    const rejected: string[] = [];

    for (const field of dedupe(incoming.dirty_fields ?? [])) {
      if (!PREFERENCE_FIELDS.has(field)) {
        rejected.push(field);
        continue;
      }

      const incomingHlc = incoming.hlc_map?.[field];
      const existingHlc = existing.hlc_map?.[field];
      if (!shouldAcceptIncoming(incomingHlc, existingHlc)) {
        rejected.push(field);
        continue;
      }

      if (field === "value") {
        merged.value = incoming.value ?? undefined;
        merged.value_type = incoming.value_type;
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

  private userPreferences(userId: string): Map<string, SyncPreference> {
    let preferences = this.preferencesByUser.get(userId);
    if (!preferences) {
      preferences = new Map();
      this.preferencesByUser.set(userId, preferences);
    }
    return preferences;
  }
}

let defaultPreferenceSyncService: PreferenceSyncServiceLike | null = null;

class DatabasePreferenceSyncService implements PreferenceSyncServiceLike {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncPreference>> {
    const result = await this.db.query(
      `
        select * from preference
        where user_id = $1 and server_version > $2
        order by server_version asc
        limit $3
      `,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToSyncPreference);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncPreference[]): Promise<SyncPreference[]> {
    const results: SyncPreference[] = [];
    for (const incoming of items) {
      const result = await withTransaction(this.db, async (tx) => {
        const existing = await selectOnePreference(tx, userId, incoming.key);
        if (!existing) return insertNewPreference(tx, userId, incoming);
        return mergeExistingPreference(tx, userId, existing, incoming);
      });
      results.push(result);
    }
    return results;
  }
}

export function resolvePreferenceSyncService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): PreferenceSyncServiceLike {
  const injected = env.__preferenceSyncService;
  if (isPreferenceSyncServiceLike(injected)) {
    return injected;
  }

  if (dbClient) {
    return new DatabasePreferenceSyncService(dbClient);
  }

  defaultPreferenceSyncService ??= new PreferenceSyncService();
  return defaultPreferenceSyncService;
}

function isPreferenceSyncServiceLike(value: unknown): value is PreferenceSyncServiceLike {
  return (
    typeof value === "object" &&
    value !== null &&
    "list" in value &&
    typeof value.list === "function" &&
    "push" in value &&
    typeof value.push === "function"
  );
}

function clonePreference(preference: SyncPreference): SyncPreference {
  return {
    ...preference,
    value: preference.value ?? undefined,
    is_deleted: preference.is_deleted ?? false,
    hlc_map: { ...(preference.hlc_map ?? {}) },
    dirty_fields: [...(preference.dirty_fields ?? [])],
    accepted_fields: preference.accepted_fields ? [...preference.accepted_fields] : undefined,
    rejected_fields: preference.rejected_fields ? [...preference.rejected_fields] : undefined,
    server_updated_at: preference.server_updated_at ?? undefined,
    created_at: preference.created_at ?? undefined,
  };
}

async function selectOnePreference(db: DbClient, userId: string, key: string): Promise<SyncPreference | null> {
  const result = await db.query(
    `select * from preference where user_id = $1 and pref_key = $2`,
    [userId, key],
  );
  const row = result.rows[0];
  return row ? rowToSyncPreference(row) : null;
}

async function insertNewPreference(db: DbClient, userId: string, incoming: SyncPreference): Promise<SyncPreference> {
  const now = new Date().toISOString();
  const canonical: SyncPreference = {
    ...clonePreference(incoming),
    is_deleted: incoming.is_deleted ?? false,
    accepted_fields: dedupe(incoming.dirty_fields ?? []),
    rejected_fields: [],
    server_version: await nextServerVersion(db),
    server_updated_at: now,
    created_at: incoming.created_at ?? now,
  };
  await db.query(
    `
      insert into preference(
        user_id, pref_key, pref_value, value_type, is_deleted, hlc_map, server_version, server_updated_at, created_at
      ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9)
    `,
    [
      userId,
      canonical.key,
      canonical.value ?? null,
      canonical.value_type,
      canonical.is_deleted ?? false,
      JSON.stringify(canonical.hlc_map ?? {}),
      canonical.server_version ?? 0,
      canonical.server_updated_at ?? now,
      canonical.created_at ?? now,
    ],
  );
  return canonical;
}

async function mergeExistingPreference(
  db: DbClient,
  userId: string,
  existing: SyncPreference,
  incoming: SyncPreference,
): Promise<SyncPreference> {
  let merged = clonePreference(existing);
  const accepted: string[] = [];
  const rejected: string[] = [];

  for (const field of dedupe(incoming.dirty_fields ?? [])) {
    const incomingHlc = incoming.hlc_map?.[field];
    const existingHlc = existing.hlc_map?.[field];
    if (!PREFERENCE_FIELDS.has(field) || !shouldAcceptIncoming(incomingHlc, existingHlc)) {
      rejected.push(field);
      continue;
    }

    if (field === "value") {
      merged.value = incoming.value ?? undefined;
      merged.value_type = incoming.value_type;
    } else {
      merged.is_deleted = incoming.is_deleted ?? false;
    }
    merged.hlc_map = { ...(merged.hlc_map ?? {}), [field]: incomingHlc! };
    accepted.push(field);
  }

  if (accepted.length === 0) {
    return { ...merged, accepted_fields: [], rejected_fields: rejected };
  }

  const updated: SyncPreference = {
    ...merged,
    accepted_fields: accepted,
    rejected_fields: rejected,
    server_version: await nextServerVersion(db),
    server_updated_at: new Date().toISOString(),
    created_at: existing.created_at ?? new Date().toISOString(),
  };
  await db.query(
    `
      update preference
      set pref_value = $1, value_type = $2, is_deleted = $3, hlc_map = $4, server_version = $5, server_updated_at = $6
      where user_id = $7 and pref_key = $8
    `,
    [
      updated.value ?? null,
      updated.value_type,
      updated.is_deleted ?? false,
      JSON.stringify(updated.hlc_map ?? {}),
      updated.server_version ?? 0,
      updated.server_updated_at ?? new Date().toISOString(),
      userId,
      updated.key,
    ],
  );
  return updated;
}

function rowToSyncPreference(row: Record<string, unknown>): SyncPreference {
  return clonePreference({
    key: String(row.pref_key),
    value: stringOrUndefined(row.pref_value),
    value_type: String(row.value_type),
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

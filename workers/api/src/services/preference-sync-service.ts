import type { DbClient } from "../db/transaction";
import type {
  MutableSyncService,
  PullResponse,
} from "./sync-service-types";
import {
  dedupe,
  shouldAcceptIncoming,
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

export function resolvePreferenceSyncService(
  env: Record<string, unknown>,
  _dbClient?: DbClient,
): PreferenceSyncServiceLike {
  const injected = env.__preferenceSyncService;
  if (isPreferenceSyncServiceLike(injected)) {
    return injected;
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

import type { DbClient } from "../db/transaction";
import { withTransaction } from "../db/transaction";
import type {
  BatchRequest,
  MutableSyncEntity,
  MutableSyncService,
  PullResponse,
} from "./sync-service-types";
import {
  dedupe,
  nextServerVersion,
  reserveServerVersions,
  parseStringMap,
  shouldAcceptIncoming,
  stringOrUndefined,
} from "./sync-utils";

// ============================================================================
// Sync Interfaces
// ============================================================================

export interface SyncFinanceAccount extends MutableSyncEntity {
  id: string;
  path: string;
  name: string;
  category: string;
  currency?: string;
  include_in_net_worth?: boolean;
  is_pass_through?: boolean;
  pass_through_expense_account_id?: string | null;
  liquid_tier?: string;
  statement_day?: number | null;
  due_day?: number | null;
  is_archived?: boolean;
  is_deleted?: boolean;
}

export interface SyncFinanceExchangeRate extends MutableSyncEntity {
  id: string;
  base_currency: string;
  quote_currency: string;
  rate_date: string;
  rate_micros: number;
  source?: string;
  is_deleted?: boolean;
}

export interface SyncFinanceClearingGroup extends MutableSyncEntity {
  id: string;
  name: string;
  group_type: string;
  anchor_currency?: string;
  target_account_id?: string | null;
  statement_cycle?: string | null;
  due_date?: string | null;
  status?: string;
  settled_at?: string | null;
  is_deleted?: boolean;
}

export interface SyncFinanceTransaction extends MutableSyncEntity {
  id: string;
  tx_date: string;
  tx_time?: string | null;
  payee?: string | null;
  summary: string;
  status?: string;
  source_plan_id?: string | null;
  period_index?: number | null;
  is_deleted?: boolean;
}

export interface SyncFinanceSplit extends MutableSyncEntity {
  id: string;
  transaction_id: string;
  account_id: string;
  amount_minor: number;
  currency?: string;
  weight_amount_minor: number;
  weight_currency?: string;
  narration?: string;
  sort_order?: number;
  is_deleted?: boolean;
}

export interface SyncFinanceSplitClearingGroup extends MutableSyncEntity {
  id: string;
  split_id: string;
  clearing_group_id: string;
  is_deleted?: boolean;
}

export interface SyncFinanceAmortizationPlan extends MutableSyncEntity {
  id: string;
  name: string;
  principal_minor: number;
  currency?: string;
  total_periods: number;
  repayment_method: string;
  annual_rate_bps?: number;
  fee_per_period_minor?: number;
  start_date: string;
  repayment_day: number;
  liability_account_id: string;
  funding_account_id?: string | null;
  interest_account_id: string;
  lifecycle_clearing_group_id: string;
  is_deleted?: boolean;
}

export interface SyncFinanceBudgetRule extends MutableSyncEntity {
  id: string;
  name: string;
  scope_type: string;
  account_prefix?: string;
  excluded_prefixes_json?: string;
  monthly_limit_minor: number;
  currency?: string;
  include_in_runway?: boolean;
  is_deleted?: boolean;
}

// ============================================================================
// In-Memory Generic Sync Service
// ============================================================================

export class MemoryFinanceSyncService<T extends MutableSyncEntity & { id: string; is_deleted?: boolean }>
  implements MutableSyncService<T>
{
  private nextVersionValue = 0;
  private readonly itemsByUser = new Map<string, Map<string, T>>();

  constructor(
    private readonly cloneFn: (item: T) => T,
    private readonly mergeFn: (merged: T, incoming: T, field: string) => void,
  ) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<T>> {
    const items = Array.from(this.userMap(userId).values())
      .filter((item) => (item.server_version ?? 0) > cursor)
      .sort((a, b) => (a.server_version ?? 0) - (b.server_version ?? 0))
      .slice(0, limit);

    return {
      items: items.map(this.cloneFn),
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: T[]): Promise<T[]> {
    return items.map((incoming) => {
      const map = this.userMap(userId);
      const existing = map.get(incoming.id);

      if (!existing) {
        const now = new Date().toISOString();
        const inserted: T = {
          ...this.cloneFn(incoming),
          is_deleted: incoming.is_deleted ?? false,
          hlc_map: { ...(incoming.hlc_map ?? {}) },
          dirty_fields: [...(incoming.dirty_fields ?? [])],
          accepted_fields: dedupe(incoming.dirty_fields ?? ["core"]),
          rejected_fields: [],
          server_version: ++this.nextVersionValue,
          server_updated_at: now,
          created_at: incoming.created_at ?? now,
        };
        map.set(inserted.id, inserted);
        return this.cloneFn(inserted);
      }

      let merged = this.cloneFn(existing);
      const accepted: string[] = [];
      const rejected: string[] = [];
      const dirtyFields = dedupe(incoming.dirty_fields ?? ["core"]);

      for (const field of dirtyFields) {
        const incomingHlc = incoming.hlc_map?.[field];
        const existingHlc = existing.hlc_map?.[field];

        if (!shouldAcceptIncoming(incomingHlc, existingHlc)) {
          rejected.push(field);
          continue;
        }

        this.mergeFn(merged, incoming, field);
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

      const updated: T = {
        ...merged,
        accepted_fields: accepted,
        rejected_fields: rejected,
        server_version: ++this.nextVersionValue,
        server_updated_at: new Date().toISOString(),
        created_at: existing.created_at ?? incoming.created_at ?? new Date().toISOString(),
      };
      map.set(updated.id, updated);
      return this.cloneFn(updated);
    });
  }

  private userMap(userId: string): Map<string, T> {
    let map = this.itemsByUser.get(userId);
    if (!map) {
      map = new Map();
      this.itemsByUser.set(userId, map);
    }
    return map;
  }
}

// ============================================================================
// Database Sync Service Implementations
// ============================================================================

export class DatabaseFinanceAccountSyncService implements MutableSyncService<SyncFinanceAccount> {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncFinanceAccount>> {
    const result = await this.db.query(
      `select * from finance_account where user_id = $1 and server_version > $2 order by server_version asc limit $3`,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToAccount);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncFinanceAccount[]): Promise<SyncFinanceAccount[]> {
    if (items.length === 0) return [];
    return await withTransaction(this.db, async (tx) => {
      let verCursor = await reserveServerVersions(tx, items.length);
      const existingRes = await tx.query(
        `select * from finance_account where user_id = $1 and id = ANY($2)`,
        [userId, items.map((i) => i.id)],
      );
      const existingMap = new Map(existingRes.rows.map((r) => [r.id as string, rowToAccount(r)]));
      const results: SyncFinanceAccount[] = [];
      for (const incoming of items) {
        const existing = existingMap.get(incoming.id) ?? null;
        if (!existing) {
          const now = new Date().toISOString();
          const nextVer = ++verCursor;
          const canonical: SyncFinanceAccount = {
            ...cloneAccount(incoming),
            is_deleted: incoming.is_deleted ?? false,
            accepted_fields: dedupe(incoming.dirty_fields ?? ["core"]),
            rejected_fields: [],
            server_version: nextVer,
            server_updated_at: now,
            created_at: incoming.created_at ?? now,
          };
          await tx.query(
            `insert into finance_account(
              id, user_id, path, name, category, currency, include_in_net_worth, is_pass_through,
              pass_through_expense_account_id, liquid_tier, statement_day, due_day, is_archived,
              is_deleted, hlc_map, server_version, server_updated_at, created_at
            ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18)`,
            [
              canonical.id,
              userId,
              canonical.path,
              canonical.name,
              canonical.category,
              canonical.currency ?? "CNY",
              canonical.include_in_net_worth ?? true,
              canonical.is_pass_through ?? false,
              canonical.pass_through_expense_account_id ?? null,
              canonical.liquid_tier ?? "Liquid",
              canonical.statement_day ?? null,
              canonical.due_day ?? null,
              canonical.is_archived ?? false,
              canonical.is_deleted ?? false,
              JSON.stringify(canonical.hlc_map ?? {}),
              canonical.server_version ?? 0,
              canonical.server_updated_at ?? now,
              canonical.created_at ?? now,
            ],
          );
          results.push(canonical);
          continue;
        }

        let merged = cloneAccount(existing);
        const accepted: string[] = [];
        const rejected: string[] = [];
        for (const field of dedupe(incoming.dirty_fields ?? ["core"])) {
          const incHlc = incoming.hlc_map?.[field];
          const exHlc = existing.hlc_map?.[field];
          if (!shouldAcceptIncoming(incHlc, exHlc)) {
            rejected.push(field);
            continue;
          }
          mergeAccount(merged, incoming, field);
          merged.hlc_map = { ...(merged.hlc_map ?? {}), [field]: incHlc! };
          accepted.push(field);
        }

        if (accepted.length === 0) {
          results.push({ ...merged, accepted_fields: [], rejected_fields: rejected });
          continue;
        }

        const nextVer = ++verCursor;
        const now = new Date().toISOString();
        const updated: SyncFinanceAccount = {
          ...merged,
          accepted_fields: accepted,
          rejected_fields: rejected,
          server_version: nextVer,
          server_updated_at: now,
          created_at: existing.created_at ?? incoming.created_at ?? now,
        };
        await tx.query(
          `update finance_account set
            path = $1, name = $2, category = $3, currency = $4, include_in_net_worth = $5,
            is_pass_through = $6, pass_through_expense_account_id = $7, liquid_tier = $8,
            statement_day = $9, due_day = $10, is_archived = $11, is_deleted = $12,
            hlc_map = $13, server_version = $14, server_updated_at = $15
          where user_id = $16 and id = $17`,
          [
            updated.path,
            updated.name,
            updated.category,
            updated.currency ?? "CNY",
            updated.include_in_net_worth ?? true,
            updated.is_pass_through ?? false,
            updated.pass_through_expense_account_id ?? null,
            updated.liquid_tier ?? "Liquid",
            updated.statement_day ?? null,
            updated.due_day ?? null,
            updated.is_archived ?? false,
            updated.is_deleted ?? false,
            JSON.stringify(updated.hlc_map ?? {}),
            updated.server_version ?? 0,
            updated.server_updated_at ?? now,
            userId,
            updated.id,
          ],
        );
        results.push(updated);
      }
      return results;
    });
  }
}

export class DatabaseFinanceExchangeRateSyncService implements MutableSyncService<SyncFinanceExchangeRate> {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncFinanceExchangeRate>> {
    const result = await this.db.query(
      `select * from finance_exchange_rate where user_id = $1 and server_version > $2 order by server_version asc limit $3`,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToExchangeRate);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncFinanceExchangeRate[]): Promise<SyncFinanceExchangeRate[]> {
    if (items.length === 0) return [];
    return await withTransaction(this.db, async (tx) => {
      let verCursor = await reserveServerVersions(tx, items.length);
      const existingRes = await tx.query(
        `select * from finance_exchange_rate where user_id = $1 and id = ANY($2)`,
        [userId, items.map((i) => i.id)],
      );
      const existingMap = new Map(existingRes.rows.map((r) => [r.id as string, rowToExchangeRate(r)]));
      const results: SyncFinanceExchangeRate[] = [];
      for (const incoming of items) {
        const existing = existingMap.get(incoming.id) ?? null;
        if (!existing) {
          const now = new Date().toISOString();
          const nextVer = ++verCursor;
          const canonical: SyncFinanceExchangeRate = {
            ...cloneExchangeRate(incoming),
            is_deleted: incoming.is_deleted ?? false,
            accepted_fields: dedupe(incoming.dirty_fields ?? ["core"]),
            rejected_fields: [],
            server_version: nextVer,
            server_updated_at: now,
            created_at: incoming.created_at ?? now,
          };
          await tx.query(
            `insert into finance_exchange_rate(
              id, user_id, base_currency, quote_currency, rate_date, rate_micros, source,
              is_deleted, hlc_map, server_version, server_updated_at, created_at
            ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)`,
            [
              canonical.id,
              userId,
              canonical.base_currency,
              canonical.quote_currency,
              canonical.rate_date,
              canonical.rate_micros,
              canonical.source ?? "Manual",
              canonical.is_deleted ?? false,
              JSON.stringify(canonical.hlc_map ?? {}),
              canonical.server_version ?? 0,
              canonical.server_updated_at ?? now,
              canonical.created_at ?? now,
            ],
          );
          results.push(canonical);
          continue;
        }

        let merged = cloneExchangeRate(existing);
        const accepted: string[] = [];
        const rejected: string[] = [];
        for (const field of dedupe(incoming.dirty_fields ?? ["core"])) {
          const incHlc = incoming.hlc_map?.[field];
          const exHlc = existing.hlc_map?.[field];
          if (!shouldAcceptIncoming(incHlc, exHlc)) {
            rejected.push(field);
            continue;
          }
          mergeExchangeRate(merged, incoming, field);
          merged.hlc_map = { ...(merged.hlc_map ?? {}), [field]: incHlc! };
          accepted.push(field);
        }

        if (accepted.length === 0) {
          results.push({ ...merged, accepted_fields: [], rejected_fields: rejected });
          continue;
        }

        const nextVer = ++verCursor;
        const now = new Date().toISOString();
        const updated: SyncFinanceExchangeRate = {
          ...merged,
          accepted_fields: accepted,
          rejected_fields: rejected,
          server_version: nextVer,
          server_updated_at: now,
          created_at: existing.created_at ?? incoming.created_at ?? now,
        };
        await tx.query(
          `update finance_exchange_rate set
            base_currency = $1, quote_currency = $2, rate_date = $3, rate_micros = $4,
            source = $5, is_deleted = $6, hlc_map = $7, server_version = $8, server_updated_at = $9
          where user_id = $10 and id = $11`,
          [
            updated.base_currency,
            updated.quote_currency,
            updated.rate_date,
            updated.rate_micros,
            updated.source ?? "Manual",
            updated.is_deleted ?? false,
            JSON.stringify(updated.hlc_map ?? {}),
            updated.server_version ?? 0,
            updated.server_updated_at ?? now,
            userId,
            updated.id,
          ],
        );
        results.push(updated);
      }
      return results;
    });
  }
}

export class DatabaseFinanceClearingGroupSyncService implements MutableSyncService<SyncFinanceClearingGroup> {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncFinanceClearingGroup>> {
    const result = await this.db.query(
      `select * from finance_clearing_group where user_id = $1 and server_version > $2 order by server_version asc limit $3`,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToClearingGroup);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncFinanceClearingGroup[]): Promise<SyncFinanceClearingGroup[]> {
    if (items.length === 0) return [];
    return await withTransaction(this.db, async (tx) => {
      let verCursor = await reserveServerVersions(tx, items.length);
      const existingRes = await tx.query(
        `select * from finance_clearing_group where user_id = $1 and id = ANY($2)`,
        [userId, items.map((i) => i.id)],
      );
      const existingMap = new Map(existingRes.rows.map((r) => [r.id as string, rowToClearingGroup(r)]));
      const results: SyncFinanceClearingGroup[] = [];
      for (const incoming of items) {
        const existing = existingMap.get(incoming.id) ?? null;
        if (!existing) {
          const now = new Date().toISOString();
          const nextVer = ++verCursor;
          const canonical: SyncFinanceClearingGroup = {
            ...cloneClearingGroup(incoming),
            is_deleted: incoming.is_deleted ?? false,
            accepted_fields: dedupe(incoming.dirty_fields ?? ["core"]),
            rejected_fields: [],
            server_version: nextVer,
            server_updated_at: now,
            created_at: incoming.created_at ?? now,
          };
          await tx.query(
            `insert into finance_clearing_group(
              id, user_id, name, group_type, anchor_currency, target_account_id, statement_cycle,
              due_date, status, settled_at, is_deleted, hlc_map, server_version, server_updated_at, created_at
            ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)`,
            [
              canonical.id,
              userId,
              canonical.name,
              canonical.group_type,
              canonical.anchor_currency ?? "CNY",
              canonical.target_account_id ?? null,
              canonical.statement_cycle ?? null,
              canonical.due_date ?? null,
              canonical.status ?? "Active",
              canonical.settled_at ?? null,
              canonical.is_deleted ?? false,
              JSON.stringify(canonical.hlc_map ?? {}),
              canonical.server_version ?? 0,
              canonical.server_updated_at ?? now,
              canonical.created_at ?? now,
            ],
          );
          results.push(canonical);
          continue;
        }

        let merged = cloneClearingGroup(existing);
        const accepted: string[] = [];
        const rejected: string[] = [];
        for (const field of dedupe(incoming.dirty_fields ?? ["core"])) {
          const incHlc = incoming.hlc_map?.[field];
          const exHlc = existing.hlc_map?.[field];
          if (!shouldAcceptIncoming(incHlc, exHlc)) {
            rejected.push(field);
            continue;
          }
          mergeClearingGroup(merged, incoming, field);
          merged.hlc_map = { ...(merged.hlc_map ?? {}), [field]: incHlc! };
          accepted.push(field);
        }

        if (accepted.length === 0) {
          results.push({ ...merged, accepted_fields: [], rejected_fields: rejected });
          continue;
        }

        const nextVer = ++verCursor;
        const now = new Date().toISOString();
        const updated: SyncFinanceClearingGroup = {
          ...merged,
          accepted_fields: accepted,
          rejected_fields: rejected,
          server_version: nextVer,
          server_updated_at: now,
          created_at: existing.created_at ?? incoming.created_at ?? now,
        };
        await tx.query(
          `update finance_clearing_group set
            name = $1, group_type = $2, anchor_currency = $3, target_account_id = $4,
            statement_cycle = $5, due_date = $6, status = $7, settled_at = $8,
            is_deleted = $9, hlc_map = $10, server_version = $11, server_updated_at = $12
          where user_id = $13 and id = $14`,
          [
            updated.name,
            updated.group_type,
            updated.anchor_currency ?? "CNY",
            updated.target_account_id ?? null,
            updated.statement_cycle ?? null,
            updated.due_date ?? null,
            updated.status ?? "Active",
            updated.settled_at ?? null,
            updated.is_deleted ?? false,
            JSON.stringify(updated.hlc_map ?? {}),
            updated.server_version ?? 0,
            updated.server_updated_at ?? now,
            userId,
            updated.id,
          ],
        );
        results.push(updated);
      }
      return results;
    });
  }
}

export class DatabaseFinanceTransactionSyncService implements MutableSyncService<SyncFinanceTransaction> {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncFinanceTransaction>> {
    const result = await this.db.query(
      `select * from finance_transaction where user_id = $1 and server_version > $2 order by server_version asc limit $3`,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToTransaction);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncFinanceTransaction[]): Promise<SyncFinanceTransaction[]> {
    if (items.length === 0) return [];
    return await withTransaction(this.db, async (tx) => {
      let verCursor = await reserveServerVersions(tx, items.length);
      const existingRes = await tx.query(
        `select * from finance_transaction where user_id = $1 and id = ANY($2)`,
        [userId, items.map((i) => i.id)],
      );
      const existingMap = new Map(existingRes.rows.map((r) => [r.id as string, rowToTransaction(r)]));
      const results: SyncFinanceTransaction[] = [];
      for (const incoming of items) {
        const existing = existingMap.get(incoming.id) ?? null;
        if (!existing) {
          const now = new Date().toISOString();
          const nextVer = ++verCursor;
          const canonical: SyncFinanceTransaction = {
            ...cloneTransaction(incoming),
            is_deleted: incoming.is_deleted ?? false,
            accepted_fields: dedupe(incoming.dirty_fields ?? ["core", "header"]),
            rejected_fields: [],
            server_version: nextVer,
            server_updated_at: now,
            created_at: incoming.created_at ?? now,
          };
          await tx.query(
            `insert into finance_transaction(
              id, user_id, tx_date, tx_time, payee, summary, status, source_plan_id,
              period_index, is_deleted, hlc_map, server_version, server_updated_at, created_at
            ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)`,
            [
              canonical.id,
              userId,
              canonical.tx_date,
              canonical.tx_time ?? null,
              canonical.payee ?? null,
              canonical.summary ?? "",
              canonical.status ?? "Cleared",
              canonical.source_plan_id ?? null,
              canonical.period_index ?? null,
              canonical.is_deleted ?? false,
              JSON.stringify(canonical.hlc_map ?? {}),
              canonical.server_version ?? 0,
              canonical.server_updated_at ?? now,
              canonical.created_at ?? now,
            ],
          );
          results.push(canonical);
          continue;
        }

        let merged = cloneTransaction(existing);
        const accepted: string[] = [];
        const rejected: string[] = [];
        for (const field of dedupe(incoming.dirty_fields ?? ["core", "header"])) {
          const incHlc = incoming.hlc_map?.[field];
          const exHlc = existing.hlc_map?.[field];
          if (!shouldAcceptIncoming(incHlc, exHlc)) {
            rejected.push(field);
            continue;
          }
          mergeTransaction(merged, incoming, field);
          merged.hlc_map = { ...(merged.hlc_map ?? {}), [field]: incHlc! };
          accepted.push(field);
        }

        if (accepted.length === 0) {
          results.push({ ...merged, accepted_fields: [], rejected_fields: rejected });
          continue;
        }

        const nextVer = ++verCursor;
        const now = new Date().toISOString();
        const updated: SyncFinanceTransaction = {
          ...merged,
          accepted_fields: accepted,
          rejected_fields: rejected,
          server_version: nextVer,
          server_updated_at: now,
          created_at: existing.created_at ?? incoming.created_at ?? now,
        };
        await tx.query(
          `update finance_transaction set
            tx_date = $1, tx_time = $2, payee = $3, summary = $4, status = $5,
            source_plan_id = $6, period_index = $7, is_deleted = $8, hlc_map = $9,
            server_version = $10, server_updated_at = $11
          where user_id = $12 and id = $13`,
          [
            updated.tx_date,
            updated.tx_time ?? null,
            updated.payee ?? null,
            updated.summary ?? "",
            updated.status ?? "Cleared",
            updated.source_plan_id ?? null,
            updated.period_index ?? null,
            updated.is_deleted ?? false,
            JSON.stringify(updated.hlc_map ?? {}),
            updated.server_version ?? 0,
            updated.server_updated_at ?? now,
            userId,
            updated.id,
          ],
        );
        results.push(updated);
      }
      return results;
    });
  }
}

export class DatabaseFinanceSplitSyncService implements MutableSyncService<SyncFinanceSplit> {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncFinanceSplit>> {
    const result = await this.db.query(
      `select * from finance_split where user_id = $1 and server_version > $2 order by server_version asc limit $3`,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToSplit);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncFinanceSplit[]): Promise<SyncFinanceSplit[]> {
    if (items.length === 0) return [];
    return await withTransaction(this.db, async (tx) => {
      let verCursor = await reserveServerVersions(tx, items.length);
      const existingRes = await tx.query(
        `select * from finance_split where user_id = $1 and id = ANY($2)`,
        [userId, items.map((i) => i.id)],
      );
      const existingMap = new Map(existingRes.rows.map((r) => [r.id as string, rowToSplit(r)]));
      const results: SyncFinanceSplit[] = [];
      for (const incoming of items) {
        const existing = existingMap.get(incoming.id) ?? null;
        if (!existing) {
          const now = new Date().toISOString();
          const nextVer = ++verCursor;
          const canonical: SyncFinanceSplit = {
            ...cloneSplit(incoming),
            is_deleted: incoming.is_deleted ?? false,
            accepted_fields: dedupe(incoming.dirty_fields ?? ["core", "content"]),
            rejected_fields: [],
            server_version: nextVer,
            server_updated_at: now,
            created_at: incoming.created_at ?? now,
          };
          await tx.query(
            `insert into finance_split(
              id, user_id, transaction_id, account_id, amount_minor, currency,
              weight_amount_minor, weight_currency, narration, sort_order,
              is_deleted, hlc_map, server_version, server_updated_at, created_at
            ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)`,
            [
              canonical.id,
              userId,
              canonical.transaction_id,
              canonical.account_id,
              canonical.amount_minor,
              canonical.currency ?? "CNY",
              canonical.weight_amount_minor,
              canonical.weight_currency ?? "CNY",
              canonical.narration ?? "",
              canonical.sort_order ?? 0,
              canonical.is_deleted ?? false,
              JSON.stringify(canonical.hlc_map ?? {}),
              canonical.server_version ?? 0,
              canonical.server_updated_at ?? now,
              canonical.created_at ?? now,
            ],
          );
          results.push(canonical);
          continue;
        }

        let merged = cloneSplit(existing);
        const accepted: string[] = [];
        const rejected: string[] = [];
        for (const field of dedupe(incoming.dirty_fields ?? ["core", "content"])) {
          const incHlc = incoming.hlc_map?.[field];
          const exHlc = existing.hlc_map?.[field];
          if (!shouldAcceptIncoming(incHlc, exHlc)) {
            rejected.push(field);
            continue;
          }
          mergeSplit(merged, incoming, field);
          merged.hlc_map = { ...(merged.hlc_map ?? {}), [field]: incHlc! };
          accepted.push(field);
        }

        if (accepted.length === 0) {
          results.push({ ...merged, accepted_fields: [], rejected_fields: rejected });
          continue;
        }

        const nextVer = ++verCursor;
        const now = new Date().toISOString();
        const updated: SyncFinanceSplit = {
          ...merged,
          accepted_fields: accepted,
          rejected_fields: rejected,
          server_version: nextVer,
          server_updated_at: now,
          created_at: existing.created_at ?? incoming.created_at ?? now,
        };
        await tx.query(
          `update finance_split set
            transaction_id = $1, account_id = $2, amount_minor = $3, currency = $4,
            weight_amount_minor = $5, weight_currency = $6, narration = $7, sort_order = $8,
            is_deleted = $9, hlc_map = $10, server_version = $11, server_updated_at = $12
          where user_id = $13 and id = $14`,
          [
            updated.transaction_id,
            updated.account_id,
            updated.amount_minor,
            updated.currency ?? "CNY",
            updated.weight_amount_minor,
            updated.weight_currency ?? "CNY",
            updated.narration ?? "",
            updated.sort_order ?? 0,
            updated.is_deleted ?? false,
            JSON.stringify(updated.hlc_map ?? {}),
            updated.server_version ?? 0,
            updated.server_updated_at ?? now,
            userId,
            updated.id,
          ],
        );
        results.push(updated);
      }
      return results;
    });
  }
}

export class DatabaseFinanceSplitClearingGroupSyncService implements MutableSyncService<SyncFinanceSplitClearingGroup> {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncFinanceSplitClearingGroup>> {
    const result = await this.db.query(
      `select * from finance_split_clearing_group where user_id = $1 and server_version > $2 order by server_version asc limit $3`,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToSplitClearingGroup);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncFinanceSplitClearingGroup[]): Promise<SyncFinanceSplitClearingGroup[]> {
    if (items.length === 0) return [];
    return await withTransaction(this.db, async (tx) => {
      let verCursor = await reserveServerVersions(tx, items.length);
      const existingRes = await tx.query(
        `select * from finance_split_clearing_group where user_id = $1 and id = ANY($2)`,
        [userId, items.map((i) => i.id)],
      );
      const existingMap = new Map(existingRes.rows.map((r) => [r.id as string, rowToSplitClearingGroup(r)]));
      const results: SyncFinanceSplitClearingGroup[] = [];
      for (const incoming of items) {
        const existing = existingMap.get(incoming.id) ?? null;
        if (!existing) {
          const now = new Date().toISOString();
          const nextVer = ++verCursor;
          const canonical: SyncFinanceSplitClearingGroup = {
            ...cloneSplitClearingGroup(incoming),
            is_deleted: incoming.is_deleted ?? false,
            accepted_fields: dedupe(incoming.dirty_fields ?? ["core", "link"]),
            rejected_fields: [],
            server_version: nextVer,
            server_updated_at: now,
            created_at: incoming.created_at ?? now,
          };
          await tx.query(
            `insert into finance_split_clearing_group(
              id, user_id, split_id, clearing_group_id, is_deleted, hlc_map, server_version, server_updated_at, created_at
            ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
            [
              canonical.id,
              userId,
              canonical.split_id,
              canonical.clearing_group_id,
              canonical.is_deleted ?? false,
              JSON.stringify(canonical.hlc_map ?? {}),
              canonical.server_version ?? 0,
              canonical.server_updated_at ?? now,
              canonical.created_at ?? now,
            ],
          );
          results.push(canonical);
          continue;
        }

        let merged = cloneSplitClearingGroup(existing);
        const accepted: string[] = [];
        const rejected: string[] = [];
        for (const field of dedupe(incoming.dirty_fields ?? ["core", "link"])) {
          const incHlc = incoming.hlc_map?.[field];
          const exHlc = existing.hlc_map?.[field];
          if (!shouldAcceptIncoming(incHlc, exHlc)) {
            rejected.push(field);
            continue;
          }
          mergeSplitClearingGroup(merged, incoming, field);
          merged.hlc_map = { ...(merged.hlc_map ?? {}), [field]: incHlc! };
          accepted.push(field);
        }

        if (accepted.length === 0) {
          results.push({ ...merged, accepted_fields: [], rejected_fields: rejected });
          continue;
        }

        const nextVer = ++verCursor;
        const now = new Date().toISOString();
        const updated: SyncFinanceSplitClearingGroup = {
          ...merged,
          accepted_fields: accepted,
          rejected_fields: rejected,
          server_version: nextVer,
          server_updated_at: now,
          created_at: existing.created_at ?? incoming.created_at ?? now,
        };
        await tx.query(
          `update finance_split_clearing_group set
            split_id = $1, clearing_group_id = $2, is_deleted = $3, hlc_map = $4,
            server_version = $5, server_updated_at = $6
          where user_id = $7 and id = $8`,
          [
            updated.split_id,
            updated.clearing_group_id,
            updated.is_deleted ?? false,
            JSON.stringify(updated.hlc_map ?? {}),
            updated.server_version ?? 0,
            updated.server_updated_at ?? now,
            userId,
            updated.id,
          ],
        );
        results.push(updated);
      }
      return results;
    });
  }
}

export class DatabaseFinanceAmortizationPlanSyncService implements MutableSyncService<SyncFinanceAmortizationPlan> {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncFinanceAmortizationPlan>> {
    const result = await this.db.query(
      `select * from finance_amortization_plan where user_id = $1 and server_version > $2 order by server_version asc limit $3`,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToAmortizationPlan);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncFinanceAmortizationPlan[]): Promise<SyncFinanceAmortizationPlan[]> {
    if (items.length === 0) return [];
    return await withTransaction(this.db, async (tx) => {
      let verCursor = await reserveServerVersions(tx, items.length);
      const existingRes = await tx.query(
        `select * from finance_amortization_plan where user_id = $1 and id = ANY($2)`,
        [userId, items.map((i) => i.id)],
      );
      const existingMap = new Map(existingRes.rows.map((r) => [r.id as string, rowToAmortizationPlan(r)]));
      const results: SyncFinanceAmortizationPlan[] = [];
      for (const incoming of items) {
        const existing = existingMap.get(incoming.id) ?? null;
        if (!existing) {
          const now = new Date().toISOString();
          const nextVer = ++verCursor;
          const canonical: SyncFinanceAmortizationPlan = {
            ...cloneAmortizationPlan(incoming),
            is_deleted: incoming.is_deleted ?? false,
            accepted_fields: dedupe(incoming.dirty_fields ?? ["core"]),
            rejected_fields: [],
            server_version: nextVer,
            server_updated_at: now,
            created_at: incoming.created_at ?? now,
          };
          await tx.query(
            `insert into finance_amortization_plan(
              id, user_id, name, principal_minor, currency, total_periods, repayment_method,
              annual_rate_bps, fee_per_period_minor, start_date, repayment_day,
              liability_account_id, funding_account_id, interest_account_id,
              lifecycle_clearing_group_id, is_deleted, hlc_map, server_version, server_updated_at, created_at
            ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20)`,
            [
              canonical.id,
              userId,
              canonical.name,
              canonical.principal_minor,
              canonical.currency ?? "CNY",
              canonical.total_periods,
              canonical.repayment_method,
              canonical.annual_rate_bps ?? 0,
              canonical.fee_per_period_minor ?? 0,
              canonical.start_date,
              canonical.repayment_day,
              canonical.liability_account_id,
              canonical.funding_account_id ?? null,
              canonical.interest_account_id,
              canonical.lifecycle_clearing_group_id,
              canonical.is_deleted ?? false,
              JSON.stringify(canonical.hlc_map ?? {}),
              canonical.server_version ?? 0,
              canonical.server_updated_at ?? now,
              canonical.created_at ?? now,
            ],
          );
          results.push(canonical);
          continue;
        }

        let merged = cloneAmortizationPlan(existing);
        const accepted: string[] = [];
        const rejected: string[] = [];
        for (const field of dedupe(incoming.dirty_fields ?? ["core"])) {
          const incHlc = incoming.hlc_map?.[field];
          const exHlc = existing.hlc_map?.[field];
          if (!shouldAcceptIncoming(incHlc, exHlc)) {
            rejected.push(field);
            continue;
          }
          mergeAmortizationPlan(merged, incoming, field);
          merged.hlc_map = { ...(merged.hlc_map ?? {}), [field]: incHlc! };
          accepted.push(field);
        }

        if (accepted.length === 0) {
          results.push({ ...merged, accepted_fields: [], rejected_fields: rejected });
          continue;
        }

        const nextVer = ++verCursor;
        const now = new Date().toISOString();
        const updated: SyncFinanceAmortizationPlan = {
          ...merged,
          accepted_fields: accepted,
          rejected_fields: rejected,
          server_version: nextVer,
          server_updated_at: now,
          created_at: existing.created_at ?? incoming.created_at ?? now,
        };
        await tx.query(
          `update finance_amortization_plan set
            name = $1, principal_minor = $2, currency = $3, total_periods = $4,
            repayment_method = $5, annual_rate_bps = $6, fee_per_period_minor = $7,
            start_date = $8, repayment_day = $9, liability_account_id = $10,
            funding_account_id = $11, interest_account_id = $12, lifecycle_clearing_group_id = $13,
            is_deleted = $14, hlc_map = $15, server_version = $16, server_updated_at = $17
          where user_id = $18 and id = $19`,
          [
            updated.name,
            updated.principal_minor,
            updated.currency ?? "CNY",
            updated.total_periods,
            updated.repayment_method,
            updated.annual_rate_bps ?? 0,
            updated.fee_per_period_minor ?? 0,
            updated.start_date,
            updated.repayment_day,
            updated.liability_account_id,
            updated.funding_account_id ?? null,
            updated.interest_account_id,
            updated.lifecycle_clearing_group_id,
            updated.is_deleted ?? false,
            JSON.stringify(updated.hlc_map ?? {}),
            updated.server_version ?? 0,
            updated.server_updated_at ?? now,
            userId,
            updated.id,
          ],
        );
        results.push(updated);
      }
      return results;
    });
  }
}

export class DatabaseFinanceBudgetRuleSyncService implements MutableSyncService<SyncFinanceBudgetRule> {
  constructor(private readonly db: DbClient) {}

  async list(userId: string, cursor: number, limit: number): Promise<PullResponse<SyncFinanceBudgetRule>> {
    const result = await this.db.query(
      `select * from finance_budget_rule where user_id = $1 and server_version > $2 order by server_version asc limit $3`,
      [userId, cursor, limit],
    );
    const items = result.rows.map(rowToBudgetRule);
    return {
      items,
      next_cursor: items.at(-1)?.server_version ?? cursor,
      has_more: items.length === limit,
    };
  }

  async push(userId: string, items: SyncFinanceBudgetRule[]): Promise<SyncFinanceBudgetRule[]> {
    if (items.length === 0) return [];
    return await withTransaction(this.db, async (tx) => {
      let verCursor = await reserveServerVersions(tx, items.length);
      const existingRes = await tx.query(
        `select * from finance_budget_rule where user_id = $1 and id = ANY($2)`,
        [userId, items.map((i) => i.id)],
      );
      const existingMap = new Map(existingRes.rows.map((r) => [r.id as string, rowToBudgetRule(r)]));
      const results: SyncFinanceBudgetRule[] = [];
      for (const incoming of items) {
        const existing = existingMap.get(incoming.id) ?? null;
        if (!existing) {
          const now = new Date().toISOString();
          const nextVer = ++verCursor;
          const canonical: SyncFinanceBudgetRule = {
            ...cloneBudgetRule(incoming),
            is_deleted: incoming.is_deleted ?? false,
            accepted_fields: dedupe(incoming.dirty_fields ?? ["core"]),
            rejected_fields: [],
            server_version: nextVer,
            server_updated_at: now,
            created_at: incoming.created_at ?? now,
          };
          await tx.query(
            `insert into finance_budget_rule(
              id, user_id, name, scope_type, account_prefix, excluded_prefixes_json,
              monthly_limit_minor, currency, include_in_runway, is_deleted,
              hlc_map, server_version, server_updated_at, created_at
            ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)`,
            [
              canonical.id,
              userId,
              canonical.name,
              canonical.scope_type,
              canonical.account_prefix ?? "Expenses:",
              canonical.excluded_prefixes_json ?? "[]",
              canonical.monthly_limit_minor,
              canonical.currency ?? "CNY",
              canonical.include_in_runway ?? true,
              canonical.is_deleted ?? false,
              JSON.stringify(canonical.hlc_map ?? {}),
              canonical.server_version ?? 0,
              canonical.server_updated_at ?? now,
              canonical.created_at ?? now,
            ],
          );
          results.push(canonical);
          continue;
        }

        let merged = cloneBudgetRule(existing);
        const accepted: string[] = [];
        const rejected: string[] = [];
        for (const field of dedupe(incoming.dirty_fields ?? ["core"])) {
          const incHlc = incoming.hlc_map?.[field];
          const exHlc = existing.hlc_map?.[field];
          if (!shouldAcceptIncoming(incHlc, exHlc)) {
            rejected.push(field);
            continue;
          }
          mergeBudgetRule(merged, incoming, field);
          merged.hlc_map = { ...(merged.hlc_map ?? {}), [field]: incHlc! };
          accepted.push(field);
        }

        if (accepted.length === 0) {
          results.push({ ...merged, accepted_fields: [], rejected_fields: rejected });
          continue;
        }

        const nextVer = ++verCursor;
        const now = new Date().toISOString();
        const updated: SyncFinanceBudgetRule = {
          ...merged,
          accepted_fields: accepted,
          rejected_fields: rejected,
          server_version: nextVer,
          server_updated_at: now,
          created_at: existing.created_at ?? incoming.created_at ?? now,
        };
        await tx.query(
          `update finance_budget_rule set
            name = $1, scope_type = $2, account_prefix = $3, excluded_prefixes_json = $4,
            monthly_limit_minor = $5, currency = $6, include_in_runway = $7,
            is_deleted = $8, hlc_map = $9, server_version = $10, server_updated_at = $11
          where user_id = $12 and id = $13`,
          [
            updated.name,
            updated.scope_type,
            updated.account_prefix ?? "Expenses:",
            updated.excluded_prefixes_json ?? "[]",
            updated.monthly_limit_minor,
            updated.currency ?? "CNY",
            updated.include_in_runway ?? true,
            updated.is_deleted ?? false,
            JSON.stringify(updated.hlc_map ?? {}),
            updated.server_version ?? 0,
            updated.server_updated_at ?? now,
            userId,
            updated.id,
          ],
        );
        results.push(updated);
      }
      return results;
    });
  }
}

// ============================================================================
// Singletons & Resolvers
// ============================================================================

let memoryAccountService: MutableSyncService<SyncFinanceAccount> | null = null;
let memoryExchangeRateService: MutableSyncService<SyncFinanceExchangeRate> | null = null;
let memoryClearingGroupService: MutableSyncService<SyncFinanceClearingGroup> | null = null;
let memoryTransactionService: MutableSyncService<SyncFinanceTransaction> | null = null;
let memorySplitService: MutableSyncService<SyncFinanceSplit> | null = null;
let memorySplitClearingGroupService: MutableSyncService<SyncFinanceSplitClearingGroup> | null = null;
let memoryAmortizationPlanService: MutableSyncService<SyncFinanceAmortizationPlan> | null = null;
let memoryBudgetRuleService: MutableSyncService<SyncFinanceBudgetRule> | null = null;

export function resolveFinanceAccountSyncService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): MutableSyncService<SyncFinanceAccount> {
  if (dbClient) return new DatabaseFinanceAccountSyncService(dbClient);
  memoryAccountService ??= new MemoryFinanceSyncService(cloneAccount, mergeAccount);
  return memoryAccountService;
}

export function resolveFinanceExchangeRateSyncService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): MutableSyncService<SyncFinanceExchangeRate> {
  if (dbClient) return new DatabaseFinanceExchangeRateSyncService(dbClient);
  memoryExchangeRateService ??= new MemoryFinanceSyncService(cloneExchangeRate, mergeExchangeRate);
  return memoryExchangeRateService;
}

export function resolveFinanceClearingGroupSyncService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): MutableSyncService<SyncFinanceClearingGroup> {
  if (dbClient) return new DatabaseFinanceClearingGroupSyncService(dbClient);
  memoryClearingGroupService ??= new MemoryFinanceSyncService(cloneClearingGroup, mergeClearingGroup);
  return memoryClearingGroupService;
}

export function resolveFinanceTransactionSyncService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): MutableSyncService<SyncFinanceTransaction> {
  if (dbClient) return new DatabaseFinanceTransactionSyncService(dbClient);
  memoryTransactionService ??= new MemoryFinanceSyncService(cloneTransaction, mergeTransaction);
  return memoryTransactionService;
}

export function resolveFinanceSplitSyncService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): MutableSyncService<SyncFinanceSplit> {
  if (dbClient) return new DatabaseFinanceSplitSyncService(dbClient);
  memorySplitService ??= new MemoryFinanceSyncService(cloneSplit, mergeSplit);
  return memorySplitService;
}

export function resolveFinanceSplitClearingGroupSyncService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): MutableSyncService<SyncFinanceSplitClearingGroup> {
  if (dbClient) return new DatabaseFinanceSplitClearingGroupSyncService(dbClient);
  memorySplitClearingGroupService ??= new MemoryFinanceSyncService(cloneSplitClearingGroup, mergeSplitClearingGroup);
  return memorySplitClearingGroupService;
}

export function resolveFinanceAmortizationPlanSyncService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): MutableSyncService<SyncFinanceAmortizationPlan> {
  if (dbClient) return new DatabaseFinanceAmortizationPlanSyncService(dbClient);
  memoryAmortizationPlanService ??= new MemoryFinanceSyncService(cloneAmortizationPlan, mergeAmortizationPlan);
  return memoryAmortizationPlanService;
}

export function resolveFinanceBudgetRuleSyncService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): MutableSyncService<SyncFinanceBudgetRule> {
  if (dbClient) return new DatabaseFinanceBudgetRuleSyncService(dbClient);
  memoryBudgetRuleService ??= new MemoryFinanceSyncService(cloneBudgetRule, mergeBudgetRule);
  return memoryBudgetRuleService;
}

// ============================================================================
// Helpers & Mapping Functions
// ============================================================================

export function cloneAccount(account: SyncFinanceAccount): SyncFinanceAccount {
  return {
    ...account,
    hlc_map: { ...(account.hlc_map ?? {}) },
    dirty_fields: [...(account.dirty_fields ?? [])],
    accepted_fields: account.accepted_fields ? [...account.accepted_fields] : undefined,
    rejected_fields: account.rejected_fields ? [...account.rejected_fields] : undefined,
  };
}

export function mergeAccount(merged: SyncFinanceAccount, incoming: SyncFinanceAccount, field: string): void {
  if (field === "deleted" || field === "is_deleted") {
    merged.is_deleted = incoming.is_deleted ?? false;
  } else {
    // core or specific field
    merged.path = incoming.path;
    merged.name = incoming.name;
    merged.category = incoming.category;
    merged.currency = incoming.currency ?? "CNY";
    merged.include_in_net_worth = incoming.include_in_net_worth ?? true;
    merged.is_pass_through = incoming.is_pass_through ?? false;
    merged.pass_through_expense_account_id = incoming.pass_through_expense_account_id ?? null;
    merged.liquid_tier = incoming.liquid_tier ?? "Liquid";
    merged.statement_day = incoming.statement_day ?? null;
    merged.due_day = incoming.due_day ?? null;
    merged.is_archived = incoming.is_archived ?? false;
    if (incoming.is_deleted !== undefined) {
      merged.is_deleted = incoming.is_deleted;
    }
  }
}

function rowToAccount(row: Record<string, unknown>): SyncFinanceAccount {
  return cloneAccount({
    id: String(row.id),
    path: String(row.path),
    name: String(row.name),
    category: String(row.category),
    currency: String(row.currency ?? "CNY"),
    include_in_net_worth: Boolean(row.include_in_net_worth),
    is_pass_through: Boolean(row.is_pass_through),
    pass_through_expense_account_id: stringOrUndefined(row.pass_through_expense_account_id),
    liquid_tier: String(row.liquid_tier ?? "Liquid"),
    statement_day: row.statement_day != null ? Number(row.statement_day) : null,
    due_day: row.due_day != null ? Number(row.due_day) : null,
    is_archived: Boolean(row.is_archived),
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

export function cloneExchangeRate(item: SyncFinanceExchangeRate): SyncFinanceExchangeRate {
  return {
    ...item,
    hlc_map: { ...(item.hlc_map ?? {}) },
    dirty_fields: [...(item.dirty_fields ?? [])],
    accepted_fields: item.accepted_fields ? [...item.accepted_fields] : undefined,
    rejected_fields: item.rejected_fields ? [...item.rejected_fields] : undefined,
  };
}

export function mergeExchangeRate(merged: SyncFinanceExchangeRate, incoming: SyncFinanceExchangeRate, field: string): void {
  if (field === "deleted" || field === "is_deleted") {
    merged.is_deleted = incoming.is_deleted ?? false;
  } else {
    merged.base_currency = incoming.base_currency;
    merged.quote_currency = incoming.quote_currency;
    merged.rate_date = incoming.rate_date;
    merged.rate_micros = incoming.rate_micros;
    merged.source = incoming.source ?? "Manual";
    if (incoming.is_deleted !== undefined) merged.is_deleted = incoming.is_deleted;
  }
}

function rowToExchangeRate(row: Record<string, unknown>): SyncFinanceExchangeRate {
  return cloneExchangeRate({
    id: String(row.id),
    base_currency: String(row.base_currency),
    quote_currency: String(row.quote_currency),
    rate_date: String(row.rate_date),
    rate_micros: Number(row.rate_micros ?? 0),
    source: String(row.source ?? "Manual"),
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

export function cloneClearingGroup(item: SyncFinanceClearingGroup): SyncFinanceClearingGroup {
  return {
    ...item,
    hlc_map: { ...(item.hlc_map ?? {}) },
    dirty_fields: [...(item.dirty_fields ?? [])],
    accepted_fields: item.accepted_fields ? [...item.accepted_fields] : undefined,
    rejected_fields: item.rejected_fields ? [...item.rejected_fields] : undefined,
  };
}

export function mergeClearingGroup(merged: SyncFinanceClearingGroup, incoming: SyncFinanceClearingGroup, field: string): void {
  if (field === "deleted" || field === "is_deleted") {
    merged.is_deleted = incoming.is_deleted ?? false;
  } else if (field === "status") {
    merged.status = incoming.status ?? "Active";
    merged.settled_at = incoming.settled_at ?? null;
  } else {
    merged.name = incoming.name;
    merged.group_type = incoming.group_type;
    merged.anchor_currency = incoming.anchor_currency ?? "CNY";
    merged.target_account_id = incoming.target_account_id ?? null;
    merged.statement_cycle = incoming.statement_cycle ?? null;
    merged.due_date = incoming.due_date ?? null;
    merged.status = incoming.status ?? "Active";
    merged.settled_at = incoming.settled_at ?? null;
    if (incoming.is_deleted !== undefined) merged.is_deleted = incoming.is_deleted;
  }
}

function rowToClearingGroup(row: Record<string, unknown>): SyncFinanceClearingGroup {
  return cloneClearingGroup({
    id: String(row.id),
    name: String(row.name),
    group_type: String(row.group_type),
    anchor_currency: String(row.anchor_currency ?? "CNY"),
    target_account_id: stringOrUndefined(row.target_account_id),
    statement_cycle: stringOrUndefined(row.statement_cycle),
    due_date: stringOrUndefined(row.due_date),
    status: String(row.status ?? "Active"),
    settled_at: stringOrUndefined(row.settled_at),
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

export function cloneTransaction(item: SyncFinanceTransaction): SyncFinanceTransaction {
  return {
    ...item,
    hlc_map: { ...(item.hlc_map ?? {}) },
    dirty_fields: [...(item.dirty_fields ?? [])],
    accepted_fields: item.accepted_fields ? [...item.accepted_fields] : undefined,
    rejected_fields: item.rejected_fields ? [...item.rejected_fields] : undefined,
  };
}

export function mergeTransaction(merged: SyncFinanceTransaction, incoming: SyncFinanceTransaction, field: string): void {
  if (field === "deleted" || field === "is_deleted") {
    merged.is_deleted = incoming.is_deleted ?? false;
  } else {
    merged.tx_date = incoming.tx_date;
    merged.tx_time = incoming.tx_time ?? null;
    merged.payee = incoming.payee ?? null;
    merged.summary = incoming.summary ?? "";
    merged.status = incoming.status ?? "Cleared";
    merged.source_plan_id = incoming.source_plan_id ?? null;
    merged.period_index = incoming.period_index ?? null;
    if (incoming.is_deleted !== undefined) merged.is_deleted = incoming.is_deleted;
  }
}

function rowToTransaction(row: Record<string, unknown>): SyncFinanceTransaction {
  return cloneTransaction({
    id: String(row.id),
    tx_date: String(row.tx_date),
    tx_time: stringOrUndefined(row.tx_time),
    payee: stringOrUndefined(row.payee),
    summary: String(row.summary ?? ""),
    status: String(row.status ?? "Cleared"),
    source_plan_id: stringOrUndefined(row.source_plan_id),
    period_index: row.period_index != null ? Number(row.period_index) : null,
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

export function cloneSplit(item: SyncFinanceSplit): SyncFinanceSplit {
  return {
    ...item,
    hlc_map: { ...(item.hlc_map ?? {}) },
    dirty_fields: [...(item.dirty_fields ?? [])],
    accepted_fields: item.accepted_fields ? [...item.accepted_fields] : undefined,
    rejected_fields: item.rejected_fields ? [...item.rejected_fields] : undefined,
  };
}

export function mergeSplit(merged: SyncFinanceSplit, incoming: SyncFinanceSplit, field: string): void {
  if (field === "deleted" || field === "is_deleted") {
    merged.is_deleted = incoming.is_deleted ?? false;
  } else {
    merged.transaction_id = incoming.transaction_id;
    merged.account_id = incoming.account_id;
    merged.amount_minor = incoming.amount_minor;
    merged.currency = incoming.currency ?? "CNY";
    merged.weight_amount_minor = incoming.weight_amount_minor;
    merged.weight_currency = incoming.weight_currency ?? "CNY";
    merged.narration = incoming.narration ?? "";
    merged.sort_order = incoming.sort_order ?? 0;
    if (incoming.is_deleted !== undefined) merged.is_deleted = incoming.is_deleted;
  }
}

function rowToSplit(row: Record<string, unknown>): SyncFinanceSplit {
  return cloneSplit({
    id: String(row.id),
    transaction_id: String(row.transaction_id),
    account_id: String(row.account_id),
    amount_minor: Number(row.amount_minor ?? 0),
    currency: String(row.currency ?? "CNY"),
    weight_amount_minor: Number(row.weight_amount_minor ?? 0),
    weight_currency: String(row.weight_currency ?? "CNY"),
    narration: String(row.narration ?? ""),
    sort_order: Number(row.sort_order ?? 0),
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

export function cloneSplitClearingGroup(item: SyncFinanceSplitClearingGroup): SyncFinanceSplitClearingGroup {
  return {
    ...item,
    hlc_map: { ...(item.hlc_map ?? {}) },
    dirty_fields: [...(item.dirty_fields ?? [])],
    accepted_fields: item.accepted_fields ? [...item.accepted_fields] : undefined,
    rejected_fields: item.rejected_fields ? [...item.rejected_fields] : undefined,
  };
}

export function mergeSplitClearingGroup(merged: SyncFinanceSplitClearingGroup, incoming: SyncFinanceSplitClearingGroup, field: string): void {
  if (field === "deleted" || field === "is_deleted") {
    merged.is_deleted = incoming.is_deleted ?? false;
  } else {
    merged.split_id = incoming.split_id;
    merged.clearing_group_id = incoming.clearing_group_id;
    if (incoming.is_deleted !== undefined) merged.is_deleted = incoming.is_deleted;
  }
}

function rowToSplitClearingGroup(row: Record<string, unknown>): SyncFinanceSplitClearingGroup {
  return cloneSplitClearingGroup({
    id: String(row.id),
    split_id: String(row.split_id),
    clearing_group_id: String(row.clearing_group_id),
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

export function cloneAmortizationPlan(item: SyncFinanceAmortizationPlan): SyncFinanceAmortizationPlan {
  return {
    ...item,
    hlc_map: { ...(item.hlc_map ?? {}) },
    dirty_fields: [...(item.dirty_fields ?? [])],
    accepted_fields: item.accepted_fields ? [...item.accepted_fields] : undefined,
    rejected_fields: item.rejected_fields ? [...item.rejected_fields] : undefined,
  };
}

export function mergeAmortizationPlan(merged: SyncFinanceAmortizationPlan, incoming: SyncFinanceAmortizationPlan, field: string): void {
  if (field === "deleted" || field === "is_deleted") {
    merged.is_deleted = incoming.is_deleted ?? false;
  } else {
    merged.name = incoming.name;
    merged.principal_minor = incoming.principal_minor;
    merged.currency = incoming.currency ?? "CNY";
    merged.total_periods = incoming.total_periods;
    merged.repayment_method = incoming.repayment_method;
    merged.annual_rate_bps = incoming.annual_rate_bps ?? 0;
    merged.fee_per_period_minor = incoming.fee_per_period_minor ?? 0;
    merged.start_date = incoming.start_date;
    merged.repayment_day = incoming.repayment_day;
    merged.liability_account_id = incoming.liability_account_id;
    merged.funding_account_id = incoming.funding_account_id ?? null;
    merged.interest_account_id = incoming.interest_account_id;
    merged.lifecycle_clearing_group_id = incoming.lifecycle_clearing_group_id;
    if (incoming.is_deleted !== undefined) merged.is_deleted = incoming.is_deleted;
  }
}

function rowToAmortizationPlan(row: Record<string, unknown>): SyncFinanceAmortizationPlan {
  return cloneAmortizationPlan({
    id: String(row.id),
    name: String(row.name),
    principal_minor: Number(row.principal_minor ?? 0),
    currency: String(row.currency ?? "CNY"),
    total_periods: Number(row.total_periods ?? 0),
    repayment_method: String(row.repayment_method),
    annual_rate_bps: Number(row.annual_rate_bps ?? 0),
    fee_per_period_minor: Number(row.fee_per_period_minor ?? 0),
    start_date: String(row.start_date),
    repayment_day: Number(row.repayment_day ?? 0),
    liability_account_id: String(row.liability_account_id),
    funding_account_id: stringOrUndefined(row.funding_account_id),
    interest_account_id: String(row.interest_account_id),
    lifecycle_clearing_group_id: String(row.lifecycle_clearing_group_id),
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

export function cloneBudgetRule(item: SyncFinanceBudgetRule): SyncFinanceBudgetRule {
  return {
    ...item,
    hlc_map: { ...(item.hlc_map ?? {}) },
    dirty_fields: [...(item.dirty_fields ?? [])],
    accepted_fields: item.accepted_fields ? [...item.accepted_fields] : undefined,
    rejected_fields: item.rejected_fields ? [...item.rejected_fields] : undefined,
  };
}

export function mergeBudgetRule(merged: SyncFinanceBudgetRule, incoming: SyncFinanceBudgetRule, field: string): void {
  if (field === "deleted" || field === "is_deleted") {
    merged.is_deleted = incoming.is_deleted ?? false;
  } else {
    merged.name = incoming.name;
    merged.scope_type = incoming.scope_type;
    merged.account_prefix = incoming.account_prefix ?? "Expenses:";
    merged.excluded_prefixes_json = incoming.excluded_prefixes_json ?? "[]";
    merged.monthly_limit_minor = incoming.monthly_limit_minor;
    merged.currency = incoming.currency ?? "CNY";
    merged.include_in_runway = incoming.include_in_runway ?? true;
    if (incoming.is_deleted !== undefined) merged.is_deleted = incoming.is_deleted;
  }
}

function rowToBudgetRule(row: Record<string, unknown>): SyncFinanceBudgetRule {
  return cloneBudgetRule({
    id: String(row.id),
    name: String(row.name),
    scope_type: String(row.scope_type),
    account_prefix: String(row.account_prefix ?? "Expenses:"),
    excluded_prefixes_json: String(row.excluded_prefixes_json ?? "[]"),
    monthly_limit_minor: Number(row.monthly_limit_minor ?? 0),
    currency: String(row.currency ?? "CNY"),
    include_in_runway: Boolean(row.include_in_runway),
    is_deleted: Boolean(row.is_deleted),
    hlc_map: parseStringMap(row.hlc_map),
    server_version: Number(row.server_version ?? 0),
    server_updated_at: stringOrUndefined(row.server_updated_at),
    created_at: stringOrUndefined(row.created_at),
  });
}

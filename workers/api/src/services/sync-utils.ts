import type { DbClient } from "../db/transaction";

export function shouldAcceptIncoming(incomingHlc?: string, existingHlc?: string): boolean {
  if (!incomingHlc) return false;
  if (!existingHlc) return true;
  return incomingHlc > existingHlc;
}

export function dedupe(values: string[]): string[] {
  return [...new Set(values)];
}

export function stringOrUndefined(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}

export function parseStringMap(value: unknown): Record<string, string> {
  if (typeof value !== "string" || value.length === 0) {
    return {};
  }

  try {
    const parsed = JSON.parse(value) as Record<string, unknown>;
    return Object.fromEntries(
      Object.entries(parsed).filter((entry): entry is [string, string] => typeof entry[1] === "string"),
    );
  } catch {
    return {};
  }
}

export async function nextServerVersion(db: DbClient): Promise<number> {
  const result = await db.query(
    `select next_value from sync_server_version where id = 1 for update`,
  );
  const current = Number(result.rows[0]?.next_value ?? 0);
  const next = current + 1;
  await db.query(
    `update sync_server_version set next_value = $1 where id = 1`,
    [next],
  );
  return next;
}

export async function hasOwnedRecord(
  db: DbClient,
  tableName: string,
  userId: string,
  id: string,
): Promise<boolean> {
  const result = await db.query(
    `select 1 from ${tableName} where user_id = $1 and id = $2`,
    [userId, id],
  );
  return result.rows.length > 0;
}

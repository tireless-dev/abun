import { describe, expect, it } from "vitest";
import { bootstrapSchema } from "../src/db/schema";
import type { DbClient } from "../src/db/transaction";

describe("bootstrapSchema", () => {
  it("adds migrations for evolved routine and task columns on existing databases", async () => {
    const statements: string[] = [];
    const client: DbClient = {
      async query(sql) {
        statements.push(normalizeSql(sql));
        return { rows: [], rowCount: 0 };
      },
    };

    await bootstrapSchema(client);

    expect(statements).toContain("alter table routine add column if not exists recurrence_rule text");
    expect(statements).toContain("alter table routine add column if not exists default_start_not_before text");
    expect(statements).toContain("alter table routine add column if not exists default_estimated_duration text");
    expect(statements).toContain("alter table task add column if not exists parent_id text");
    expect(statements).toContain("alter table task add column if not exists routine_id text");
    expect(statements).toContain("alter table task add column if not exists start_not_before text");
    expect(statements).toContain("alter table task add column if not exists end_not_after text");
    expect(statements).toContain("alter table task add column if not exists estimated_duration text");
    expect(statements).toContain("alter table task_event add column if not exists postponed_json text");
    expect(statements).toContain("alter table task_event add column if not exists server_updated_at text");
  });
});

function normalizeSql(sql: string): string {
  return sql.replace(/\s+/g, " ").trim().toLowerCase();
}

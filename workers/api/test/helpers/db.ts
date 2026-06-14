import { bootstrapSchema } from "../../src/db/schema";
import type { DbClient } from "../../src/db/transaction";

type TableRow = Record<string, unknown>;
type DatabaseState = Map<string, TableRow[]>;

export interface TestDbContext {
  client: DbClient;
  query: DbClient["query"];
}

export async function withTestDb<T>(
  block: (db: TestDbContext) => Promise<T>,
): Promise<T> {
  const client = createFakeDbClient();
  await bootstrapSchema(client);

  try {
    return await block({
      client,
      query: client.query.bind(client),
    });
  } finally {
    await client.end?.();
  }
}

function createFakeDbClient(): DbClient {
  let state = new Map<string, TableRow[]>();
  let transactionState: DatabaseState | null = null;

  const cloneState = (source: DatabaseState): DatabaseState =>
    new Map(
      Array.from(source.entries(), ([tableName, rows]) => [
        tableName,
        rows.map((row) => ({ ...row })),
      ]),
    );

  const activeState = (): DatabaseState => transactionState ?? state;

  const query: DbClient["query"] = async (sql, values = []) => {
    const normalized = normalizeSql(sql);

    if (normalized === "begin") {
      if (transactionState !== null) {
        throw new Error("Nested transactions are not supported in tests");
      }

      transactionState = cloneState(state);
      return { rows: [], rowCount: 0 };
    }

    if (normalized === "commit") {
      if (transactionState === null) {
        throw new Error("Cannot commit without an open transaction");
      }

      state = transactionState;
      transactionState = null;
      return { rows: [], rowCount: 0 };
    }

    if (normalized === "rollback") {
      transactionState = null;
      return { rows: [], rowCount: 0 };
    }

    const tables = activeState();

    if (normalized.startsWith("drop table if exists ")) {
      const tableName = normalized.slice("drop table if exists ".length).trim();
      tables.delete(tableName);
      return { rows: [], rowCount: 0 };
    }

    if (normalized.startsWith("create table if not exists ")) {
      const tableName = readTableName(normalized, "create table if not exists ");
      if (!tables.has(tableName)) {
        tables.set(tableName, []);
      }
      return { rows: [], rowCount: 0 };
    }

    if (normalized.startsWith("create table ")) {
      const tableName = readTableName(normalized, "create table ");
      tables.set(tableName, []);
      return { rows: [], rowCount: 0 };
    }

    if (
      normalized ===
      "insert into sync_server_version (id, next_value) values (1, 0) on conflict (id) do nothing"
    ) {
      const rows = tables.get("sync_server_version") ?? [];
      if (!rows.some((row) => row.id === 1)) {
        rows.push({ id: 1, next_value: 0 });
      }
      tables.set("sync_server_version", rows);
      return { rows: [], rowCount: 1 };
    }

    if (normalized.startsWith("insert into ")) {
      const match = normalized.match(
        /^insert into ([a-z0-9_]+)\(([^)]+)\) values \(([^)]+)\)$/,
      );
      if (!match) {
        throw new Error(`Unsupported insert statement in test DB: ${sql}`);
      }

      const [, tableName, columnList] = match;
      const columns = columnList.split(",").map((column) => column.trim());
      const row: TableRow = {};
      columns.forEach((column, index) => {
        row[column] = values[index];
      });

      const rows = tables.get(tableName) ?? [];
      rows.push(row);
      tables.set(tableName, rows);
      return { rows: [], rowCount: 1 };
    }

    if (normalized.startsWith("select count(*)::int as count from ")) {
      const tableName = normalized
        .slice("select count(*)::int as count from ".length)
        .trim();
      const rows = tables.get(tableName) ?? [];

      return {
        rows: [{ count: rows.length }],
        rowCount: 1,
      };
    }

    throw new Error(`Unsupported SQL in test DB: ${sql}`);
  };

  return {
    query,
    end: async () => {
      transactionState = null;
      state = new Map();
    },
  };
}

function normalizeSql(sql: string): string {
  return sql.replace(/\s+/g, " ").trim().toLowerCase();
}

function readTableName(sql: string, prefix: string): string {
  const remainder = sql.slice(prefix.length);
  const tableName = remainder.split(" ", 1)[0];

  if (!tableName) {
    throw new Error(`Unable to read table name from SQL: ${sql}`);
  }

  return tableName;
}

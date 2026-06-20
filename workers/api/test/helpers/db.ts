import { bootstrapSchema } from "../../src/db/schema";
import type { DbClient } from "../../src/db/transaction";

type TableRow = Record<string, unknown>;
type DatabaseState = Map<string, TableRow[]>;
type TableSchemaState = Map<string, Set<string>>;

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
  let schemas = new Map<string, Set<string>>();
  let transactionSchemas: TableSchemaState | null = null;

  const cloneState = (source: DatabaseState): DatabaseState =>
    new Map(
      Array.from(source.entries(), ([tableName, rows]) => [
        tableName,
        rows.map((row) => ({ ...row })),
      ]),
    );

  const cloneSchemas = (source: TableSchemaState): TableSchemaState =>
    new Map(
      Array.from(source.entries(), ([tableName, columns]) => [
        tableName,
        new Set(columns),
      ]),
    );

  const activeState = (): DatabaseState => transactionState ?? state;
  const activeSchemas = (): TableSchemaState => transactionSchemas ?? schemas;

  const query: DbClient["query"] = async (sql, values = []) => {
    const normalized = normalizeSql(sql);

    if (normalized === "begin") {
      if (transactionState !== null) {
        throw new Error("Nested transactions are not supported in tests");
      }

      transactionState = cloneState(state);
      transactionSchemas = cloneSchemas(schemas);
      return { rows: [], rowCount: 0 };
    }

    if (normalized === "commit") {
      if (transactionState === null) {
        throw new Error("Cannot commit without an open transaction");
      }

      state = transactionState;
      schemas = transactionSchemas ?? schemas;
      transactionState = null;
      transactionSchemas = null;
      return { rows: [], rowCount: 0 };
    }

    if (normalized === "rollback") {
      transactionState = null;
      transactionSchemas = null;
      return { rows: [], rowCount: 0 };
    }

    const tables = activeState();
    const tableSchemas = activeSchemas();

    if (normalized.startsWith("drop table if exists ")) {
      const tableName = normalized.slice("drop table if exists ".length).trim();
      tables.delete(tableName);
      tableSchemas.delete(tableName);
      return { rows: [], rowCount: 0 };
    }

    if (normalized.startsWith("create table if not exists ")) {
      const { tableName, columns } = readCreateTableDefinition(normalized, "create table if not exists ");
      if (!tables.has(tableName)) {
        tables.set(tableName, []);
      }
      tableSchemas.set(tableName, columns);
      return { rows: [], rowCount: 0 };
    }

    if (normalized.startsWith("create table ")) {
      const { tableName, columns } = readCreateTableDefinition(normalized, "create table ");
      tables.set(tableName, []);
      tableSchemas.set(tableName, columns);
      return { rows: [], rowCount: 0 };
    }

    if (normalized.startsWith("alter table ")) {
      const match = normalized.match(/^alter table ([a-z0-9_]+) add column if not exists ([a-z0-9_]+) /);
      if (!match) {
        throw new Error(`Unsupported alter statement in test DB: ${sql}`);
      }
      const [, tableName, columnName] = match;
      const columns = tableSchemas.get(tableName) ?? new Set<string>();
      columns.add(columnName);
      tableSchemas.set(tableName, columns);
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
      const knownColumns = tableSchemas.get(tableName);
      if (!knownColumns) {
        throw new Error(`Unknown table in test DB insert: ${tableName}`);
      }
      const row: TableRow = {};
      columns.forEach((column, index) => {
        if (!knownColumns.has(column)) {
          throw new Error(`Unknown column ${column} for table ${tableName} in test DB`);
        }
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
      transactionSchemas = null;
      state = new Map();
      schemas = new Map();
    },
  };
}

function normalizeSql(sql: string): string {
  return sql.replace(/\s+/g, " ").trim().toLowerCase();
}

function readCreateTableDefinition(sql: string, prefix: string): { tableName: string; columns: Set<string> } {
  const remainder = sql.slice(prefix.length);
  const tableName = remainder.split(" ", 1)[0];

  if (!tableName) {
    throw new Error(`Unable to read table name from SQL: ${sql}`);
  }

  const body = sql.slice(sql.indexOf("(") + 1, sql.lastIndexOf(")"));
  const columns = body
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => entry.split(" ", 1)[0])
    .filter((entry) => entry !== "primary" && entry !== "unique")
    .reduce<Set<string>>((set, column) => {
      set.add(column);
      return set;
    }, new Set());

  return { tableName, columns };
}

export interface DbQueryResult {
  rows: Record<string, unknown>[];
  rowCount?: number;
}

export interface DbClient {
  query: (sql: string, values?: readonly unknown[]) => Promise<DbQueryResult>;
  end?: () => Promise<void> | void;
}

export async function withTransaction<T>(
  client: DbClient,
  block: (tx: DbClient) => Promise<T>,
): Promise<T> {
  await client.query("BEGIN");

  try {
    const result = await block(client);
    await client.query("COMMIT");
    return result;
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  }
}

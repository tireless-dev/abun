import { Client } from "pg";
import { getDbUrl, type WorkerEnv } from "../env";
import type { DbClient } from "./transaction";

export async function createDbClient(
  connectionStringOrEnv: string | Partial<WorkerEnv>,
): Promise<DbClient> {
  const shouldRelaxTls =
    typeof connectionStringOrEnv === "string" ||
    !(typeof connectionStringOrEnv.HYPERDRIVE?.connectionString === "string" &&
      connectionStringOrEnv.HYPERDRIVE.connectionString.length > 0);
  const connectionString =
    typeof connectionStringOrEnv === "string"
      ? connectionStringOrEnv
      : getDbUrl(connectionStringOrEnv);
  const client = new Client({
    connectionString,
    ssl: shouldRelaxTls ? { rejectUnauthorized: false } : undefined,
  });
  await client.connect();
  return client;
}

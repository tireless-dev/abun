import { Client } from "pg";
import { getDbUrl, type WorkerEnv } from "../env";
import type { DbClient } from "./transaction";

export async function createDbClient(
  connectionStringOrEnv: string | Partial<WorkerEnv>,
): Promise<DbClient> {
  const connectionString =
    typeof connectionStringOrEnv === "string"
      ? connectionStringOrEnv
      : getDbUrl(connectionStringOrEnv);
  const client = new Client({ connectionString });
  await client.connect();
  return client;
}

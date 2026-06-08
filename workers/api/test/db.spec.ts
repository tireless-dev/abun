import { describe, expect, it } from "vitest";
import { withTransaction } from "../src/db/transaction";
import { withTestDb } from "./helpers/db";

describe("database transactions", () => {
  it("rolls back writes when the transaction body throws", async () => {
    await withTestDb(async ({ client, query }) => {
      await query(`drop table if exists txn_test`);
      await query(`create table txn_test (id text primary key)`);

      await expect(
        withTransaction(client, async (transactionClient) => {
          await transactionClient.query(
            `insert into txn_test(id) values ($1)`,
            ["row-1"],
          );

          throw new Error("boom");
        }),
      ).rejects.toThrow("boom");

      const result = await query(`select count(*)::int as count from txn_test`);
      expect(result.rows[0].count).toBe(0);
    });
  });
});

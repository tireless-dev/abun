import { describe, expect, it } from "vitest";
import { getDbUrl, hasDbUrl } from "../src/env";

describe("worker env database resolution", () => {
  it("prefers Hyperdrive when both Hyperdrive and DB_URL are present", () => {
    expect(
      getDbUrl({
        DB_URL: "postgres://db-url",
        HYPERDRIVE: { connectionString: "postgres://hyperdrive-url" },
      }),
    ).toBe("postgres://hyperdrive-url");
  });

  it("treats Hyperdrive as a valid database binding", () => {
    expect(
      hasDbUrl({
        HYPERDRIVE: { connectionString: "postgres://hyperdrive-url" },
      }),
    ).toBe(true);
  });
});

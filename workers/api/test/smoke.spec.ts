import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("api worker smoke test", () => {
  it("returns the sync server banner at the root route", async () => {
    const handler = worker as ExportedHandler;
    const fetchHandler = handler.fetch as NonNullable<ExportedHandler["fetch"]>;
    const request = new Request("http://example.com/");
    const response = await fetchHandler(
      request as never,
      {} as never,
      {} as ExecutionContext,
    );

    expect(response.status).toBe(200);
    expect(await response.text()).toBe("abun sync server");
  });
});

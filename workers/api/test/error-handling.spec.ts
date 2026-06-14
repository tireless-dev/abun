import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("api worker auth handling", () => {
  it("returns a 401 JSON error for /api/tasks when bearer auth is required", async () => {
    const handler = worker as ExportedHandler;
    const fetchHandler = handler.fetch as NonNullable<ExportedHandler["fetch"]>;
    const request = new Request("http://example.com/api/tasks");

    const response = await fetchHandler(
      request as never,
      { ABUN_REQUIRE_AUTH: "true" } as never,
      {} as ExecutionContext,
    );

    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({
      message: "Missing or invalid bearer token",
    });
  });

  it("treats ABUN_REQUIRE_AUTH as case-insensitive", async () => {
    const handler = worker as ExportedHandler;
    const fetchHandler = handler.fetch as NonNullable<ExportedHandler["fetch"]>;
    const request = new Request("http://example.com/api/tasks");

    const response = await fetchHandler(
      request as never,
      { ABUN_REQUIRE_AUTH: "TRUE" } as never,
      {} as ExecutionContext,
    );

    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({
      message: "Missing or invalid bearer token",
    });
  });

  it("returns a bare JSON array for /api/tasks", async () => {
    const handler = worker as ExportedHandler;
    const fetchHandler = handler.fetch as NonNullable<ExportedHandler["fetch"]>;
    const request = new Request("http://example.com/api/tasks", {
      headers: {
        authorization: "Bearer token-123",
      },
    });

    const response = await fetchHandler(
      request as never,
      { ABUN_REQUIRE_AUTH: "true" } as never,
      {} as ExecutionContext,
    );

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual([]);
  });
});

import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("task sync routes", () => {
  it("accepts newer HLC values and rejects older conflicting task updates", async () => {
    const handler = worker as ExportedHandler;
    const fetchHandler = handler.fetch as NonNullable<ExportedHandler["fetch"]>;
    const env = { ABUN_REQUIRE_AUTH: "true" } as never;

    const firstResponse = await fetchHandler(
      new Request("http://example.com/api/sync/tasks", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "task-1",
              title: "Write spec",
              hlc_map: { title: "1715959378000-0001-deviceA" },
              dirty_fields: ["title"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(firstResponse.status).toBe(200);
    expect(await firstResponse.json()).toEqual({
      items: [
        expect.objectContaining({
          id: "task-1",
          title: "Write spec",
          accepted_fields: ["title"],
          rejected_fields: [],
        }),
      ],
    });

    const secondResponse = await fetchHandler(
      new Request("http://example.com/api/sync/tasks", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "task-1",
              title: "Old edit",
              hlc_map: { title: "1715959378000-0000-deviceB" },
              dirty_fields: ["title"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(secondResponse.status).toBe(200);
    expect(await secondResponse.json()).toEqual({
      items: [
        expect.objectContaining({
          id: "task-1",
          title: "Write spec",
          accepted_fields: [],
          rejected_fields: ["title"],
        }),
      ],
    });
  });

  it("lists synced tasks with pull cursor fields", async () => {
    const handler = worker as ExportedHandler;
    const fetchHandler = handler.fetch as NonNullable<ExportedHandler["fetch"]>;
    const env = { ABUN_REQUIRE_AUTH: "true" } as never;

    await fetchHandler(
      new Request("http://example.com/api/sync/tasks", {
        method: "POST",
        headers: {
          authorization: "Bearer user-2",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "task-2",
              title: "Pull me",
              hlc_map: { title: "1715959378000-0001-deviceA" },
              dirty_fields: ["title"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    const response = await fetchHandler(
      new Request("http://example.com/api/sync/tasks?cursor=0&limit=10", {
        method: "GET",
        headers: {
          authorization: "Bearer user-2",
        },
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({
      items: [
        expect.objectContaining({
          id: "task-2",
          title: "Pull me",
        }),
      ],
      next_cursor: expect.any(Number),
      has_more: false,
    });
  });
});

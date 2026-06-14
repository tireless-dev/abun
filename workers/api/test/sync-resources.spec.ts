import { describe, expect, it } from "vitest";
import worker from "../src/index";
import { createPassThroughAuthEnv } from "./helpers/auth";

describe("additional sync resource routes", () => {
  it("syncs preferences per user and returns pull cursor payloads", async () => {
    const fetchHandler = getFetchHandler();
    const env = createPassThroughAuthEnv();

    const pushResponse = await fetchHandler(
      new Request("http://example.com/api/sync/preferences", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              key: "task.default_alarm_lead_minutes",
              value: "20",
              value_type: "INT",
              hlc_map: { value: "1715959378000-0001-deviceA" },
              dirty_fields: ["value"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(pushResponse.status).toBe(200);
    expect(await pushResponse.json()).toEqual({
      items: [
        expect.objectContaining({
          key: "task.default_alarm_lead_minutes",
          value: "20",
          accepted_fields: ["value"],
          rejected_fields: [],
        }),
      ],
    });

    const pullResponse = await fetchHandler(
      new Request("http://example.com/api/sync/preferences?cursor=0&limit=10", {
        method: "GET",
        headers: {
          authorization: "Bearer user-1",
        },
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(pullResponse.status).toBe(200);
    expect(await pullResponse.json()).toEqual({
      items: [
        expect.objectContaining({
          key: "task.default_alarm_lead_minutes",
          value: "20",
          value_type: "INT",
        }),
      ],
      next_cursor: expect.any(Number),
      has_more: false,
    });

    const otherUserPullResponse = await fetchHandler(
      new Request("http://example.com/api/sync/preferences?cursor=0&limit=10", {
        method: "GET",
        headers: {
          authorization: "Bearer user-2",
        },
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(otherUserPullResponse.status).toBe(200);
    expect(await otherUserPullResponse.json()).toEqual({
      items: [],
      next_cursor: 0,
      has_more: false,
    });
  });

  it("syncs routines and alarms after the parent task exists", async () => {
    const fetchHandler = getFetchHandler();
    const env = createPassThroughAuthEnv();

    await pushTask(fetchHandler, env, "user-1", "task-1");

    const routineResponse = await fetchHandler(
      new Request("http://example.com/api/sync/routines", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "routine-1",
              template_title: "Morning plan",
              template_detail: "Review backlog",
              recurrence_rule: "RRULE:FREQ=DAILY",
              default_start_not_before: "2026-05-25T09:00:00Z",
              default_estimated_duration: "PT30M",
              is_active: true,
              hlc_map: {
                template_title: "1715959378000-0001-deviceA",
                template_detail: "1715959378000-0001-deviceA",
                recurrence_rule: "1715959378000-0001-deviceA",
              },
              dirty_fields: ["template_title", "template_detail", "recurrence_rule"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(routineResponse.status).toBe(200);
    expect(await routineResponse.json()).toEqual({
      items: [
        expect.objectContaining({
          id: "routine-1",
          template_title: "Morning plan",
          accepted_fields: ["template_title", "template_detail", "recurrence_rule"],
        }),
      ],
    });

    const alarmResponse = await fetchHandler(
      new Request("http://example.com/api/sync/alarms", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "alarm-1",
              task_id: "task-1",
              trigger_time: "2026-05-25T08:50:00Z",
              is_active: true,
              hlc_map: {
                trigger_time: "1715959378000-0001-deviceA",
              },
              dirty_fields: ["trigger_time"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(alarmResponse.status).toBe(200);
    expect(await alarmResponse.json()).toEqual({
      items: [
        expect.objectContaining({
          id: "alarm-1",
          task_id: "task-1",
          trigger_time: "2026-05-25T08:50:00Z",
          accepted_fields: ["trigger_time"],
          rejected_fields: [],
        }),
      ],
    });
  });

  it("keeps task events append-only and rejects duplicate event ids", async () => {
    const fetchHandler = getFetchHandler();
    const env = createPassThroughAuthEnv();

    await pushTask(fetchHandler, env, "user-1", "task-events-1");

    const firstResponse = await fetchHandler(
      new Request("http://example.com/api/sync/task-events", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "event-1",
              task_id: "task-events-1",
              journal_date: "2026-05-24",
              event_type: "PROGRESSED",
              event_time: "2026-05-24T09:00:00Z",
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
          id: "event-1",
          accepted: true,
        }),
      ],
    });

    const duplicateResponse = await fetchHandler(
      new Request("http://example.com/api/sync/task-events", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "event-1",
              task_id: "task-events-1",
              journal_date: "2026-05-24",
              event_type: "PROGRESSED",
              event_time: "2026-05-24T09:00:00Z",
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(duplicateResponse.status).toBe(200);
    expect(await duplicateResponse.json()).toEqual({
      items: [
        expect.objectContaining({
          id: "event-1",
          accepted: false,
        }),
      ],
    });

    const pullResponse = await fetchHandler(
      new Request("http://example.com/api/sync/task-events?cursor=0&limit=10", {
        method: "GET",
        headers: {
          authorization: "Bearer user-1",
        },
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(pullResponse.status).toBe(200);
    expect(await pullResponse.json()).toEqual({
      items: [
        expect.objectContaining({
          id: "event-1",
          event_type: "PROGRESSED",
          accepted: true,
        }),
      ],
      next_cursor: expect.any(Number),
      has_more: false,
    });
  });

  it("syncs pomodoro sessions and preserves field-level conflict decisions", async () => {
    const fetchHandler = getFetchHandler();
    const env = createPassThroughAuthEnv();

    await pushTask(fetchHandler, env, "user-1", "task-session-1");

    const firstResponse = await fetchHandler(
      new Request("http://example.com/api/sync/pomodoro-sessions", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "session-1",
              task_id: "task-session-1",
              phase: "FOCUS",
              state: "ACTIVE",
              started_at: "2026-05-25T09:00:00Z",
              ends_at: "2026-05-25T09:25:00Z",
              duration_minutes: 25,
              note: "Deep work",
              task_update: "NONE",
              hlc_map: {
                state: "1715959378000-0001-deviceA",
                note: "1715959378000-0001-deviceA",
              },
              dirty_fields: ["state", "note"],
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
          id: "session-1",
          state: "ACTIVE",
          note: "Deep work",
          accepted_fields: ["state", "note"],
        }),
      ],
    });

    const secondResponse = await fetchHandler(
      new Request("http://example.com/api/sync/pomodoro-sessions", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "session-1",
              task_id: "task-session-1",
              phase: "FOCUS",
              state: "COMPLETED",
              started_at: "2026-05-25T09:00:00Z",
              ends_at: "2026-05-25T09:25:00Z",
              completed_at: "2026-05-25T09:26:00Z",
              duration_minutes: 25,
              note: "Stale write",
              task_update: "COMPLETE",
              hlc_map: {
                state: "1715959378000-0002-deviceA",
                note: "1715959378000-0000-deviceB",
                completed_at: "1715959378000-0002-deviceA",
                task_update: "1715959378000-0002-deviceA",
              },
              dirty_fields: ["state", "note", "completed_at", "task_update"],
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
          id: "session-1",
          state: "COMPLETED",
          note: "Deep work",
          accepted_fields: ["state", "completed_at", "task_update"],
          rejected_fields: ["note"],
        }),
      ],
    });
  });
});

function getFetchHandler(): NonNullable<ExportedHandler["fetch"]> {
  const handler = worker as ExportedHandler;
  return handler.fetch as NonNullable<ExportedHandler["fetch"]>;
}

async function pushTask(
  fetchHandler: NonNullable<ExportedHandler["fetch"]>,
  env: never,
  userId: string,
  taskId: string,
): Promise<void> {
  const response = await fetchHandler(
    new Request("http://example.com/api/sync/tasks", {
      method: "POST",
      headers: {
        authorization: `Bearer ${userId}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        items: [
          {
            id: taskId,
            title: "Parent task",
            hlc_map: { title: "1715959378000-0001-deviceA" },
            dirty_fields: ["title"],
          },
        ],
      }),
    }) as never,
    env,
    {} as ExecutionContext,
  );

  expect(response.status).toBe(200);
}

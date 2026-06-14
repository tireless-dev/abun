import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("business api routes", () => {
  it("supports the mutable resource flow and sync visibility", async () => {
    const fetchHandler = getFetchHandler();
    const env = { ABUN_REQUIRE_AUTH: "true" } as never;

    const routineResponse = await fetchHandler(
      jsonRequest("http://example.com/api/routines", "user-1", {
        id: "routine-1",
        template_title: "Morning plan",
        template_detail: "Review backlog and choose the day target",
        recurrence_rule: "RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0",
        default_start_not_before: "2026-05-25T09:00:00Z",
        default_estimated_duration: "PT30M",
      }, "POST"),
      env,
      {} as ExecutionContext,
    );
    expect(routineResponse.status).toBe(201);
    expect(await routineResponse.json()).toEqual(expect.objectContaining({
      id: "routine-1",
      template_detail: "Review backlog and choose the day target",
      recurrence_rule: "RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0",
      default_start_not_before: "2026-05-25T09:00:00Z",
      default_estimated_duration: "PT30M",
    }));

    const taskResponse = await fetchHandler(
      jsonRequest("http://example.com/api/tasks", "user-1", {
        id: "task-1",
        title: "Plan day",
        detail: "Work through the new planning window",
        routine_id: "routine-1",
        start_not_before: "2026-05-25T09:00:00Z",
        end_not_after: "2026-05-25T17:00:00Z",
        estimated_duration: "PT2H",
      }, "POST"),
      env,
      {} as ExecutionContext,
    );
    expect(taskResponse.status).toBe(201);
    expect(await taskResponse.json()).toEqual(expect.objectContaining({
      id: "task-1",
      detail: "Work through the new planning window",
      start_not_before: "2026-05-25T09:00:00Z",
      end_not_after: "2026-05-25T17:00:00Z",
      estimated_duration: "PT2H",
      routine_id: "routine-1",
    }));

    const alarmResponse = await fetchHandler(
      jsonRequest("http://example.com/api/alarms", "user-1", {
        id: "alarm-1",
        task_id: "task-1",
        trigger_time: "2026-05-25T08:50:00Z",
      }, "POST"),
      env,
      {} as ExecutionContext,
    );
    expect(alarmResponse.status).toBe(201);
    expect(await alarmResponse.json()).toEqual(expect.objectContaining({
      id: "alarm-1",
      task_id: "task-1",
    }));

    const sessionResponse = await fetchHandler(
      jsonRequest("http://example.com/api/pomodoro-sessions", "user-1", {
        id: "session-1",
        task_id: "task-1",
        phase: "FOCUS",
        state: "ACTIVE",
        started_at: "2026-05-25T09:00:00Z",
        ends_at: "2026-05-25T09:25:00Z",
        duration_minutes: 25,
      }, "POST"),
      env,
      {} as ExecutionContext,
    );
    expect(sessionResponse.status).toBe(201);
    expect(await sessionResponse.json()).toEqual(expect.objectContaining({
      id: "session-1",
      task_id: "task-1",
      state: "ACTIVE",
    }));

    const patchedRoutine = await fetchHandler(
      jsonRequest("http://example.com/api/routines/routine-1", "user-1", {
        template_detail: "Start with the most important project",
        recurrence_rule: "RRULE:FREQ=DAILY;BYHOUR=10;BYMINUTE=30",
        default_start_not_before: "2026-05-25T10:30:00Z",
        default_estimated_duration: "PT45M",
        is_active: false,
      }, "PATCH"),
      env,
      {} as ExecutionContext,
    );
    expect(patchedRoutine.status).toBe(200);
    expect(await patchedRoutine.json()).toEqual(expect.objectContaining({
      is_active: false,
      template_detail: "Start with the most important project",
      recurrence_rule: "RRULE:FREQ=DAILY;BYHOUR=10;BYMINUTE=30",
    }));

    const patchedTask = await fetchHandler(
      jsonRequest("http://example.com/api/tasks/task-1", "user-1", {
        detail: "Updated plan",
        start_not_before: "2026-05-25T10:00:00Z",
        end_not_after: "2026-05-25T18:00:00Z",
        estimated_duration: "PT3H",
      }, "PATCH"),
      env,
      {} as ExecutionContext,
    );
    expect(patchedTask.status).toBe(200);
    expect(await patchedTask.json()).toEqual(expect.objectContaining({
      detail: "Updated plan",
      start_not_before: "2026-05-25T10:00:00Z",
      end_not_after: "2026-05-25T18:00:00Z",
      estimated_duration: "PT3H",
    }));

    const patchedAlarm = await fetchHandler(
      jsonRequest("http://example.com/api/alarms/alarm-1", "user-1", {
        is_active: false,
      }, "PATCH"),
      env,
      {} as ExecutionContext,
    );
    expect(patchedAlarm.status).toBe(200);
    expect(await patchedAlarm.json()).toEqual(expect.objectContaining({
      is_active: false,
    }));

    const patchedSession = await fetchHandler(
      jsonRequest("http://example.com/api/pomodoro-sessions/session-1", "user-1", {
        state: "COMPLETED",
        completed_at: "2026-05-25T09:26:00Z",
        note: "Finished focus block",
        task_update: "COMPLETE",
      }, "PATCH"),
      env,
      {} as ExecutionContext,
    );
    expect(patchedSession.status).toBe(200);
    expect(await patchedSession.json()).toEqual(expect.objectContaining({
      state: "COMPLETED",
      task_update: "COMPLETE",
      note: "Finished focus block",
    }));

    const deletedTask = await fetchHandler(
      new Request("http://example.com/api/tasks/task-1", {
        method: "DELETE",
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(deletedTask.status).toBe(200);
    expect(await deletedTask.json()).toEqual(expect.objectContaining({
      id: "task-1",
      is_deleted: true,
    }));

    const taskEvents = await fetchHandler(
      new Request("http://example.com/api/tasks/task-1/events", {
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(taskEvents.status).toBe(200);
    expect(await taskEvents.json()).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ event_type: "DELETED" }),
      ]),
    );

    const syncedTasks = await fetchHandler(
      new Request("http://example.com/api/sync/tasks?cursor=0&limit=10", {
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(syncedTasks.status).toBe(200);
    expect(await syncedTasks.json()).toEqual({
      items: [
        expect.objectContaining({
          id: "task-1",
          is_deleted: true,
          detail: "Updated plan",
          start_not_before: "2026-05-25T10:00:00Z",
          end_not_after: "2026-05-25T18:00:00Z",
          estimated_duration: "PT3H",
        }),
      ],
      next_cursor: expect.any(Number),
      has_more: false,
    });
  });

  it("keeps task events append-only and exposes task status and journal views", async () => {
    const fetchHandler = getFetchHandler();
    const env = { ABUN_REQUIRE_AUTH: "true" } as never;

    await fetchHandler(
      jsonRequest("http://example.com/api/tasks", "user-1", {
        id: "task-journal-1",
        title: "Ledger task",
        journal_date: "2026-05-24",
        event_time: "2026-05-24T08:00:00Z",
      }, "POST"),
      env,
      {} as ExecutionContext,
    );

    const initialEvents = await fetchHandler(
      new Request("http://example.com/api/tasks/task-journal-1/events", {
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(await initialEvents.json()).toEqual([
      expect.objectContaining({ event_type: "CREATED" }),
    ]);

    const initialStatus = await fetchHandler(
      new Request("http://example.com/api/tasks/task-journal-1/status", {
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(await initialStatus.json()).toEqual({ status: "PENDING" });

    await fetchHandler(
      jsonRequest("http://example.com/api/tasks/task-journal-1", "user-1", {
        title: "Ledger task renamed",
      }, "PATCH"),
      env,
      {} as ExecutionContext,
    );

    const progressed = await fetchHandler(
      jsonRequest("http://example.com/api/tasks/task-journal-1/events", "user-1", {
        id: "event-1",
        task_id: "ignored-by-route",
        journal_date: "2026-05-24",
        event_type: "PROGRESSED",
        event_time: "2026-05-24T09:00:00Z",
      }, "POST"),
      env,
      {} as ExecutionContext,
    );
    expect(progressed.status).toBe(201);

    const postponed = await fetchHandler(
      jsonRequest("http://example.com/api/tasks/task-journal-1/events", "user-1", {
        id: "event-postponed",
        task_id: "ignored-by-route",
        journal_date: "2026-05-24",
        event_type: "POSTPONED",
        postponed: {
          previous_start_not_before: "2026-05-24T09:00:00Z",
          new_start_not_before: "2026-05-25T09:00:00Z",
          previous_end_not_after: "2026-05-24T17:00:00Z",
          new_end_not_after: "2026-05-25T17:00:00Z",
        },
        event_time: "2026-05-24T09:05:00Z",
      }, "POST"),
      env,
      {} as ExecutionContext,
    );
    expect(postponed.status).toBe(201);

    const extraCreated = await fetchHandler(
      jsonRequest("http://example.com/api/tasks/task-journal-1/events", "user-1", {
        id: "event-extra",
        task_id: "ignored-by-route",
        journal_date: "2026-05-24",
        event_type: "CREATED",
        event_time: "2026-05-24T09:10:00Z",
      }, "POST"),
      env,
      {} as ExecutionContext,
    );
    expect(extraCreated.status).toBe(201);

    const syncCompleted = await fetchHandler(
      jsonRequest("http://example.com/api/sync/task-events", "user-1", {
        items: [
          {
            id: "event-2",
            task_id: "task-journal-1",
            journal_date: "2026-05-28",
            event_type: "COMPLETED",
            event_time: "2026-05-28T10:00:00Z",
          },
        ],
      }, "POST", true),
      env,
      {} as ExecutionContext,
    );
    expect(syncCompleted.status).toBe(200);

    const duplicate = await fetchHandler(
      jsonRequest("http://example.com/api/sync/task-events", "user-1", {
        items: [
          {
            id: "event-1",
            task_id: "task-journal-1",
            journal_date: "2026-05-24",
            event_type: "PROGRESSED",
            event_time: "2026-05-24T09:00:00Z",
          },
        ],
      }, "POST", true),
      env,
      {} as ExecutionContext,
    );
    expect(await duplicate.json()).toEqual({
      items: [
        expect.objectContaining({ accepted: false }),
      ],
    });

    const finalStatus = await fetchHandler(
      new Request("http://example.com/api/tasks/task-journal-1/status", {
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(await finalStatus.json()).toEqual({ status: "COMPLETED" });

    const journal = await fetchHandler(
      new Request("http://example.com/api/journals/2026-05-24", {
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(await journal.json()).toEqual([
      expect.objectContaining({ event_type: "CREATED" }),
      expect.objectContaining({ event_type: "PROGRESSED" }),
      expect.objectContaining({
        event_type: "POSTPONED",
        postponed: expect.objectContaining({ new_start_not_before: "2026-05-25T09:00:00Z" }),
      }),
      expect.objectContaining({ event_type: "CREATED" }),
    ]);
  });

  it("returns not found and bad request responses like the Ktor server", async () => {
    const fetchHandler = getFetchHandler();
    const env = { ABUN_REQUIRE_AUTH: "true" } as never;

    const badAlarm = await fetchHandler(
      jsonRequest("http://example.com/api/alarms", "user-1", {
        id: "alarm-missing",
        task_id: "missing-task",
        trigger_time: "2026-05-25T08:50:00Z",
      }, "POST"),
      env,
      {} as ExecutionContext,
    );
    expect(badAlarm.status).toBe(400);
    expect(await badAlarm.json()).toEqual({
      message: "task_id does not belong to current user",
    });

    const missingTask = await fetchHandler(
      new Request("http://example.com/api/tasks/missing-task", {
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(missingTask.status).toBe(404);

    const missingPreferenceDelete = await fetchHandler(
      new Request("http://example.com/api/preferences/unknown", {
        method: "DELETE",
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(missingPreferenceDelete.status).toBe(404);
  });
});

function getFetchHandler(): (request: Request, env: never, ctx: ExecutionContext) => Promise<Response> {
  const handler = worker as ExportedHandler;
  return handler.fetch as unknown as (request: Request, env: never, ctx: ExecutionContext) => Promise<Response>;
}

function jsonRequest(
  url: string,
  userId: string,
  body: unknown,
  method: string,
  rawBody = false,
): Request {
  return new Request(url, {
    method,
    headers: {
      authorization: `Bearer ${userId}`,
      "content-type": "application/json",
    },
    body: rawBody ? JSON.stringify(body) : JSON.stringify(body),
  });
}

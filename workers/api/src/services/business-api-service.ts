import { HttpError } from "../http/errors";
import type { AlarmSyncServiceLike, SyncAlarm } from "./alarm-sync-service";
import type { PomodoroSessionSyncServiceLike, SyncPomodoroSession } from "./pomodoro-session-sync-service";
import type { PreferenceSyncServiceLike, SyncPreference } from "./preference-sync-service";
import type { RoutineSyncServiceLike, SyncRoutine } from "./routine-sync-service";
import type { TaskEventSyncServiceLike, SyncTaskEvent } from "./task-event-sync-service";
import type { TaskSyncServiceLike, SyncTask } from "./task-sync-service";

export interface PreferencePutRequest {
  value?: string | null;
  value_type: string;
}

export interface RoutineUpsertRequest {
  id?: string | null;
  template_title: string;
  template_detail?: string | null;
  recurrence_rule: string;
  default_start_not_before?: string | null;
  default_estimated_duration?: string | null;
  is_active?: boolean;
}

export interface RoutinePatchRequest {
  template_title?: string | null;
  template_detail?: string | null;
  recurrence_rule?: string | null;
  default_start_not_before?: string | null;
  default_estimated_duration?: string | null;
  is_active?: boolean | null;
}

export interface TaskUpsertRequest {
  id?: string | null;
  title: string;
  detail?: string | null;
  parent_id?: string | null;
  routine_id?: string | null;
  start_not_before?: string | null;
  end_not_after?: string | null;
  estimated_duration?: string | null;
  journal_date?: string | null;
  event_time?: string | null;
}

export interface TaskPatchRequest {
  title?: string | null;
  detail?: string | null;
  parent_id?: string | null;
  routine_id?: string | null;
  start_not_before?: string | null;
  end_not_after?: string | null;
  estimated_duration?: string | null;
}

export interface AlarmUpsertRequest {
  id?: string | null;
  task_id: string;
  trigger_time: string;
  is_active?: boolean;
}

export interface AlarmPatchRequest {
  trigger_time?: string | null;
  is_active?: boolean | null;
}

export interface PomodoroSessionUpsertRequest {
  id?: string | null;
  task_id?: string | null;
  phase: string;
  state: string;
  started_at: string;
  ends_at: string;
  completed_at?: string | null;
  duration_minutes: number;
  note?: string | null;
  task_update?: string;
}

export interface PomodoroSessionPatchRequest {
  task_id?: string | null;
  phase?: string | null;
  state?: string | null;
  started_at?: string | null;
  ends_at?: string | null;
  completed_at?: string | null;
  duration_minutes?: number | null;
  note?: string | null;
  task_update?: string | null;
}

export interface TaskEventCreateRequest {
  id?: string | null;
  task_id: string;
  journal_date: string;
  event_type: string;
  content?: string | null;
  postponed?: SyncTaskEvent["postponed"];
  event_time: string;
}

export interface JournalEntry {
  task_id: string;
  event_id: string;
  event_type: string;
  content?: string | null;
  postponed?: SyncTaskEvent["postponed"];
  event_time: string;
}

export class BusinessApiService {
  constructor(
    private readonly preferences: PreferenceSyncServiceLike,
    private readonly routines: RoutineSyncServiceLike,
    private readonly tasks: TaskSyncServiceLike,
    private readonly alarms: AlarmSyncServiceLike,
    private readonly taskEvents: TaskEventSyncServiceLike,
    private readonly pomodoroSessions: PomodoroSessionSyncServiceLike,
  ) {}

  async listPreferences(userId: string): Promise<SyncPreference[]> {
    return listAll(this.preferences, userId);
  }

  async getPreference(userId: string, key: string): Promise<SyncPreference | null> {
    return findByKey(await this.listPreferences(userId), key);
  }

  async putPreference(userId: string, key: string, request: PreferencePutRequest): Promise<SyncPreference> {
    const existing = await this.getPreference(userId, key);
    const nextHlc = nextClockValue(existing?.hlc_map?.value);
    const incoming: SyncPreference = {
      key,
      value: request.value ?? undefined,
      value_type: request.value_type,
      is_deleted: false,
      hlc_map: {
        ...(existing?.hlc_map ?? {}),
        value: nextHlc,
      },
      dirty_fields: ["value"],
      created_at: existing?.created_at,
    };
    return pushOne(this.preferences, userId, incoming);
  }

  async deletePreference(userId: string, key: string): Promise<SyncPreference | null> {
    const existing = await this.getPreference(userId, key);
    if (!existing) return null;
    return pushOne(this.preferences, userId, {
      ...existing,
      is_deleted: true,
      hlc_map: {
        ...(existing.hlc_map ?? {}),
        delete: nextClockValue(existing.hlc_map?.delete),
      },
      dirty_fields: ["delete"],
    });
  }

  async listRoutines(userId: string): Promise<SyncRoutine[]> {
    return listAll(this.routines, userId);
  }

  async getRoutine(userId: string, id: string): Promise<SyncRoutine | null> {
    return findById(await this.listRoutines(userId), id);
  }

  async createRoutine(userId: string, request: RoutineUpsertRequest): Promise<SyncRoutine> {
    const anchor = nextClockValue();
    return pushOne(this.routines, userId, {
      id: request.id ?? crypto.randomUUID(),
      template_title: request.template_title,
      template_detail: request.template_detail ?? undefined,
      recurrence_rule: request.recurrence_rule,
      default_start_not_before: request.default_start_not_before ?? undefined,
      default_estimated_duration: request.default_estimated_duration ?? undefined,
      is_active: request.is_active ?? true,
      hlc_map: {
        template_title: anchor,
        template_detail: nextClockValue(anchor),
        recurrence_rule: nextClockValue(),
        default_start_not_before: nextClockValue(),
        default_estimated_duration: nextClockValue(),
        active: nextClockValue(),
      },
      dirty_fields: [
        "template_title",
        "template_detail",
        "recurrence_rule",
        "default_start_not_before",
        "default_estimated_duration",
        "active",
      ],
    });
  }

  async patchRoutine(userId: string, id: string, request: RoutinePatchRequest): Promise<SyncRoutine | null> {
    const existing = await this.getRoutine(userId, id);
    if (!existing) return null;
    const dirty: string[] = [];
    const hlc = { ...(existing.hlc_map ?? {}) };
    const incoming: SyncRoutine = { ...existing };

    if (request.template_title != null) {
      incoming.template_title = request.template_title;
      hlc.template_title = nextClockValue(hlc.template_title);
      dirty.push("template_title");
    }
    if (request.template_detail != null && request.template_detail !== existing.template_detail) {
      incoming.template_detail = request.template_detail;
      hlc.template_detail = nextClockValue(hlc.template_detail);
      dirty.push("template_detail");
    }
    if (request.recurrence_rule != null && request.recurrence_rule !== existing.recurrence_rule) {
      incoming.recurrence_rule = request.recurrence_rule;
      hlc.recurrence_rule = nextClockValue(hlc.recurrence_rule);
      dirty.push("recurrence_rule");
    }
    if (request.default_start_not_before != null && request.default_start_not_before !== existing.default_start_not_before) {
      incoming.default_start_not_before = request.default_start_not_before;
      hlc.default_start_not_before = nextClockValue(hlc.default_start_not_before);
      dirty.push("default_start_not_before");
    }
    if (request.default_estimated_duration != null && request.default_estimated_duration !== existing.default_estimated_duration) {
      incoming.default_estimated_duration = request.default_estimated_duration;
      hlc.default_estimated_duration = nextClockValue(hlc.default_estimated_duration);
      dirty.push("default_estimated_duration");
    }
    if (request.is_active != null && request.is_active !== existing.is_active) {
      incoming.is_active = request.is_active;
      hlc.active = nextClockValue(hlc.active);
      dirty.push("active");
    }
    if (dirty.length === 0) return existing;
    incoming.hlc_map = hlc;
    incoming.dirty_fields = dirty;
    return pushOne(this.routines, userId, incoming);
  }

  async deleteRoutine(userId: string, id: string): Promise<SyncRoutine | null> {
    const existing = await this.getRoutine(userId, id);
    if (!existing) return null;
    return pushOne(this.routines, userId, {
      ...existing,
      is_deleted: true,
      hlc_map: {
        ...(existing.hlc_map ?? {}),
        delete: nextClockValue(existing.hlc_map?.delete),
      },
      dirty_fields: ["delete"],
    });
  }

  async listTasks(userId: string): Promise<SyncTask[]> {
    return listAll(this.tasks, userId);
  }

  async getTask(userId: string, id: string): Promise<SyncTask | null> {
    return findById(await this.listTasks(userId), id);
  }

  async createTask(userId: string, request: TaskUpsertRequest): Promise<SyncTask> {
    await this.assertTaskRelationships(userId, request.parent_id ?? null, request.routine_id ?? null);
    const anchor = nextClockValue();
    const task = await pushOne(this.tasks, userId, {
      id: request.id ?? crypto.randomUUID(),
      parent_id: request.parent_id ?? undefined,
      routine_id: request.routine_id ?? undefined,
      title: request.title,
      detail: request.detail ?? undefined,
      start_not_before: request.start_not_before ?? undefined,
      end_not_after: request.end_not_after ?? undefined,
      estimated_duration: request.estimated_duration ?? undefined,
      hlc_map: {
        title: anchor,
        ...(request.parent_id ? { parent: nextClockValue(anchor) } : {}),
        ...(request.routine_id ? { routine: nextClockValue(anchor) } : {}),
        ...(request.detail ? { detail: nextClockValue(anchor) } : {}),
        ...(request.start_not_before ? { start_not_before: nextClockValue(anchor) } : {}),
        ...(request.end_not_after ? { end_not_after: nextClockValue(anchor) } : {}),
        ...(request.estimated_duration ? { estimated_duration: nextClockValue(anchor) } : {}),
      },
      dirty_fields: [
        "title",
        ...(request.parent_id ? ["parent"] : []),
        ...(request.routine_id ? ["routine"] : []),
        ...(request.detail ? ["detail"] : []),
        ...(request.start_not_before ? ["start_not_before"] : []),
        ...(request.end_not_after ? ["end_not_after"] : []),
        ...(request.estimated_duration ? ["estimated_duration"] : []),
      ],
    });

    if (!(await this.deriveTaskStatus(userId, task.id))) {
      const eventTime = request.event_time ?? new Date().toISOString();
      await this.createTaskEvent(userId, task.id, {
        task_id: task.id,
        journal_date: request.journal_date ?? eventTime.substring(0, 10),
        event_type: "CREATED",
        event_time: eventTime,
      });
    }

    return task;
  }

  async patchTask(userId: string, id: string, request: TaskPatchRequest): Promise<SyncTask | null> {
    const existing = await this.getTask(userId, id);
    if (!existing) return null;
    await this.assertTaskRelationships(userId, request.parent_id ?? existing.parent_id ?? null, request.routine_id ?? existing.routine_id ?? null);
    const dirty: string[] = [];
    const hlc = { ...(existing.hlc_map ?? {}) };
    const incoming: SyncTask = { ...existing };

    if (request.title != null) {
      incoming.title = request.title;
      hlc.title = nextClockValue(hlc.title);
      dirty.push("title");
    }
    if (request.detail !== existing.detail) {
      incoming.detail = request.detail ?? undefined;
      hlc.detail = nextClockValue(hlc.detail);
      dirty.push("detail");
    }
    if (request.parent_id !== existing.parent_id) {
      incoming.parent_id = request.parent_id ?? undefined;
      hlc.parent = nextClockValue(hlc.parent);
      dirty.push("parent");
    }
    if (request.routine_id !== existing.routine_id) {
      incoming.routine_id = request.routine_id ?? undefined;
      hlc.routine = nextClockValue(hlc.routine);
      dirty.push("routine");
    }
    if (request.start_not_before !== existing.start_not_before) {
      incoming.start_not_before = request.start_not_before ?? undefined;
      hlc.start_not_before = nextClockValue(hlc.start_not_before);
      dirty.push("start_not_before");
    }
    if (request.end_not_after !== existing.end_not_after) {
      incoming.end_not_after = request.end_not_after ?? undefined;
      hlc.end_not_after = nextClockValue(hlc.end_not_after);
      dirty.push("end_not_after");
    }
    if (request.estimated_duration !== existing.estimated_duration) {
      incoming.estimated_duration = request.estimated_duration ?? undefined;
      hlc.estimated_duration = nextClockValue(hlc.estimated_duration);
      dirty.push("estimated_duration");
    }
    if (dirty.length === 0) return existing;
    incoming.hlc_map = hlc;
    incoming.dirty_fields = dirty;
    return pushOne(this.tasks, userId, incoming);
  }

  async deleteTask(userId: string, id: string): Promise<SyncTask | null> {
    const existing = await this.getTask(userId, id);
    if (!existing) return null;
    const task = await pushOne(this.tasks, userId, {
      ...existing,
      is_deleted: true,
      hlc_map: {
        ...(existing.hlc_map ?? {}),
        delete: nextClockValue(existing.hlc_map?.delete),
      },
      dirty_fields: ["delete"],
    });
    const eventTime = new Date().toISOString();
    await this.createTaskEvent(userId, task.id, {
      task_id: task.id,
      journal_date: eventTime.substring(0, 10),
      event_type: "DELETED",
      event_time: eventTime,
    });
    return task;
  }

  async listAlarms(userId: string): Promise<SyncAlarm[]> {
    return listAll(this.alarms, userId);
  }

  async getAlarm(userId: string, id: string): Promise<SyncAlarm | null> {
    return findById(await this.listAlarms(userId), id);
  }

  async createAlarm(userId: string, request: AlarmUpsertRequest): Promise<SyncAlarm> {
    return pushOne(this.alarms, userId, {
      id: request.id ?? crypto.randomUUID(),
      task_id: request.task_id,
      trigger_time: request.trigger_time,
      is_active: request.is_active ?? true,
      hlc_map: {
        trigger: nextClockValue(),
        active: nextClockValue(),
      },
      dirty_fields: ["trigger", "active"],
    });
  }

  async patchAlarm(userId: string, id: string, request: AlarmPatchRequest): Promise<SyncAlarm | null> {
    const existing = await this.getAlarm(userId, id);
    if (!existing) return null;
    const dirty: string[] = [];
    const hlc = { ...(existing.hlc_map ?? {}) };
    const incoming: SyncAlarm = { ...existing };

    if (request.trigger_time != null && request.trigger_time !== existing.trigger_time) {
      incoming.trigger_time = request.trigger_time;
      hlc.trigger = nextClockValue(hlc.trigger ?? hlc.trigger_time);
      dirty.push("trigger");
    }
    if (request.is_active != null && request.is_active !== existing.is_active) {
      incoming.is_active = request.is_active;
      hlc.active = nextClockValue(hlc.active);
      dirty.push("active");
    }
    if (dirty.length === 0) return existing;
    incoming.hlc_map = hlc;
    incoming.dirty_fields = dirty;
    return pushOne(this.alarms, userId, incoming);
  }

  async deleteAlarm(userId: string, id: string): Promise<SyncAlarm | null> {
    const existing = await this.getAlarm(userId, id);
    if (!existing) return null;
    return pushOne(this.alarms, userId, {
      ...existing,
      is_deleted: true,
      hlc_map: {
        ...(existing.hlc_map ?? {}),
        delete: nextClockValue(existing.hlc_map?.delete),
      },
      dirty_fields: ["delete"],
    });
  }

  async listPomodoroSessions(userId: string): Promise<SyncPomodoroSession[]> {
    return listAll(this.pomodoroSessions, userId);
  }

  async getPomodoroSession(userId: string, id: string): Promise<SyncPomodoroSession | null> {
    return findById(await this.listPomodoroSessions(userId), id);
  }

  async createPomodoroSession(userId: string, request: PomodoroSessionUpsertRequest): Promise<SyncPomodoroSession> {
    const anchor = nextClockValue();
    return pushOne(this.pomodoroSessions, userId, {
      id: request.id ?? crypto.randomUUID(),
      task_id: request.task_id ?? undefined,
      phase: request.phase,
      state: request.state,
      started_at: request.started_at,
      ends_at: request.ends_at,
      completed_at: request.completed_at ?? undefined,
      duration_minutes: request.duration_minutes,
      note: request.note ?? undefined,
      task_update: request.task_update ?? "NONE",
      hlc_map: {
        task: anchor,
        timing: nextClockValue(anchor),
        state: nextClockValue(),
        note: nextClockValue(),
        outcome: nextClockValue(),
      },
      dirty_fields: ["task", "timing", "state", "note", "outcome"],
    });
  }

  async patchPomodoroSession(userId: string, id: string, request: PomodoroSessionPatchRequest): Promise<SyncPomodoroSession | null> {
    const existing = await this.getPomodoroSession(userId, id);
    if (!existing) return null;
    const dirty: string[] = [];
    const hlc = { ...(existing.hlc_map ?? {}) };
    const incoming: SyncPomodoroSession = { ...existing };

    if (request.task_id !== undefined && request.task_id !== existing.task_id) {
      incoming.task_id = request.task_id ?? undefined;
      hlc.task = nextClockValue(hlc.task);
      dirty.push("task");
    }
    if (request.phase != null && request.phase !== existing.phase) {
      incoming.phase = request.phase;
      hlc.timing = nextClockValue(hlc.timing ?? hlc.phase);
      dirty.push("timing");
    }
    if (
      request.phase != null ||
      request.started_at != null ||
      request.ends_at != null ||
      request.duration_minutes != null
    ) {
      const nextPhase = request.phase ?? existing.phase;
      const nextStartedAt = request.started_at ?? existing.started_at;
      const nextEndsAt = request.ends_at ?? existing.ends_at;
      const nextDuration = request.duration_minutes ?? existing.duration_minutes;
      if (
        nextPhase !== existing.phase ||
        nextStartedAt !== existing.started_at ||
        nextEndsAt !== existing.ends_at ||
        nextDuration !== existing.duration_minutes
      ) {
        incoming.phase = nextPhase;
        incoming.started_at = nextStartedAt;
        incoming.ends_at = nextEndsAt;
        incoming.duration_minutes = nextDuration;
        hlc.timing = nextClockValue(hlc.timing ?? hlc.phase);
        if (!dirty.includes("timing")) dirty.push("timing");
      }
    }
    if (request.state != null || request.completed_at !== undefined) {
      const nextState = request.state ?? existing.state;
      const nextCompletedAt = request.completed_at ?? existing.completed_at;
      if (nextState !== existing.state || nextCompletedAt !== existing.completed_at) {
        incoming.state = nextState;
        incoming.completed_at = nextCompletedAt ?? undefined;
        hlc.state = nextClockValue(hlc.state);
        dirty.push("state");
      }
    }
    if (request.note !== undefined && request.note !== existing.note) {
      incoming.note = request.note ?? undefined;
      hlc.note = nextClockValue(hlc.note);
      dirty.push("note");
    }
    if (request.task_update != null && request.task_update !== existing.task_update) {
      incoming.task_update = request.task_update;
      hlc.outcome = nextClockValue(hlc.outcome ?? hlc.task_update);
      dirty.push("outcome");
    }
    if (dirty.length === 0) return existing;
    incoming.hlc_map = hlc;
    incoming.dirty_fields = dirty;
    return pushOne(this.pomodoroSessions, userId, incoming);
  }

  async deletePomodoroSession(userId: string, id: string): Promise<SyncPomodoroSession | null> {
    const existing = await this.getPomodoroSession(userId, id);
    if (!existing) return null;
    return pushOne(this.pomodoroSessions, userId, {
      ...existing,
      is_deleted: true,
      hlc_map: {
        ...(existing.hlc_map ?? {}),
        delete: nextClockValue(existing.hlc_map?.delete),
      },
      dirty_fields: ["delete"],
    });
  }

  async listTaskEvents(userId: string, taskId: string): Promise<SyncTaskEvent[] | null> {
    if (!(await this.tasks.exists(userId, taskId))) return null;
    return (await listAll(this.taskEvents, userId))
      .filter((event) => event.task_id === taskId)
      .sort(compareEventsAsc);
  }

  async createTaskEvent(userId: string, taskId: string, request: TaskEventCreateRequest): Promise<SyncTaskEvent> {
    return pushOne(this.taskEvents, userId, {
      id: request.id ?? crypto.randomUUID(),
      task_id: taskId,
      journal_date: request.journal_date,
      event_type: request.event_type,
      content: request.content ?? undefined,
      postponed: request.postponed ?? undefined,
      event_time: request.event_time,
    });
  }

  async deriveTaskStatus(userId: string, taskId: string): Promise<string | null> {
    const events = await this.listTaskEvents(userId, taskId);
    if (!events || events.length === 0) return null;
    const latest = [...events]
      .filter((event) => !event.is_deleted)
      .sort(compareEventsDesc)[0];
    if (!latest) return "UNKNOWN";
    switch (latest.event_type) {
      case "PROGRESSED":
        return "IN_PROGRESS";
      case "COMPLETED":
        return "COMPLETED";
      case "DELETED":
      case "MISSED":
      case "SKIPPED":
      case "CANCELLED":
        return "CANCELLED";
      default:
        return "PENDING";
    }
  }

  async journal(userId: string, date: string): Promise<JournalEntry[]> {
    return (await listAll(this.taskEvents, userId))
      .filter((event) => event.journal_date === date && !event.is_deleted)
      .sort(compareEventsAsc)
      .map((event) => ({
        task_id: event.task_id,
        event_id: event.id,
        event_type: event.event_type,
        content: event.content ?? undefined,
        postponed: event.postponed ?? undefined,
        event_time: event.event_time,
      }));
  }

  private async assertTaskRelationships(userId: string, parentId: string | null, routineId: string | null): Promise<void> {
    if (parentId && !(await this.tasks.exists(userId, parentId))) {
      throw new HttpError(400, "parent_id does not belong to current user");
    }
    if (routineId && !(await this.getRoutine(userId, routineId))) {
      throw new HttpError(400, "routine_id does not belong to current user");
    }
  }
}

async function listAll<T>(
  service: { list(userId: string, cursor: number, limit: number): { items: T[]; next_cursor: number; has_more: boolean } | Promise<{ items: T[]; next_cursor: number; has_more: boolean }> },
  userId: string,
): Promise<T[]> {
  const items: T[] = [];
  let cursor = 0;
  while (true) {
    const page = await Promise.resolve(service.list(userId, cursor, 500));
    items.push(...page.items);
    if (!page.has_more) return items;
    cursor = page.next_cursor;
  }
}

async function pushOne<T>(
  service: { push(userId: string, items: T[]): T[] | Promise<T[]> },
  userId: string,
  item: T,
): Promise<T> {
  try {
    return (await Promise.resolve(service.push(userId, [item])))[0]!;
  } catch (error) {
    if (error instanceof HttpError) throw error;
    if (error instanceof Error) {
      throw new HttpError(400, error.message);
    }
    throw error;
  }
}

function findById<T extends { id: string }>(items: T[], id: string): T | null {
  return items.find((item) => item.id === id) ?? null;
}

function findByKey<T extends { key: string }>(items: T[], key: string): T | null {
  return items.find((item) => item.key === key) ?? null;
}

function compareEventsAsc(left: SyncTaskEvent, right: SyncTaskEvent): number {
  return left.event_time.localeCompare(right.event_time) || (left.created_at ?? "").localeCompare(right.created_at ?? "");
}

function compareEventsDesc(left: SyncTaskEvent, right: SyncTaskEvent): number {
  return right.event_time.localeCompare(left.event_time) || (right.created_at ?? "").localeCompare(left.created_at ?? "");
}

let clockSequence = 0;

function nextClockValue(previous?: string): string {
  clockSequence += 1;
  const next = `${Date.now()}-${String(clockSequence).padStart(4, "0")}-server`;
  if (!previous) return next;
  return next > previous ? next : `${Date.now()}-${String(clockSequence + 1).padStart(4, "0")}-server`;
}

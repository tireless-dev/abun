export interface ApiClientOptions {
  baseUrl?: string;
  bearerToken?: string;
  fetchImpl?: typeof fetch;
  maxReadRetries?: number;
}

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type TaskEventType = 'CREATED' | 'PROGRESSED' | 'COMPLETED' | 'CANCELLED' | 'ALARM_FIRED';
export type PomodoroPhase = 'FOCUS' | 'SHORT_BREAK' | 'LONG_BREAK';
export type PomodoroSessionState = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type PomodoroTaskUpdate = 'NONE' | 'PROGRESS' | 'COMPLETE' | 'CANCEL';
export type PreferenceValueType = 'STRING' | 'INT' | 'ENUM';

export interface OtpVerifyResponse {
  access_token: string;
  user_id: string;
}

export interface PreferenceResponse {
  key: string;
  value: string | null;
  value_type: PreferenceValueType;
  is_deleted: boolean;
  server_version: number;
}

export interface TaskResponse {
  id: string;
  parent_id?: string | null;
  routine_id?: string | null;
  title: string;
  is_deleted: boolean;
  server_version: number;
}

export interface TaskStatusResponse {
  status: TaskStatus;
}

export interface TaskEventResponse {
  id: string;
  task_id: string;
  journal_date: string;
  event_type: TaskEventType;
  content?: string | null;
  event_time: string;
  is_deleted: boolean;
  server_version: number;
}

export interface TaskUpsertRequest {
  id?: string;
  title: string;
  parent_id?: string | null;
  routine_id?: string | null;
}

export interface TaskPatchRequest {
  title?: string;
  parent_id?: string | null;
  routine_id?: string | null;
}

export interface TaskEventCreateRequest {
  id?: string;
  journal_date: string;
  event_type: TaskEventType;
  content?: string | null;
  event_time: string;
}

export interface RoutineResponse {
  id: string;
  template_title: string;
  cron_schedule: string;
  timezone: string;
  is_active: boolean;
  is_deleted: boolean;
  server_version: number;
}

export interface RoutineUpsertRequest {
  id?: string;
  template_title: string;
  cron_schedule: string;
  timezone: string;
  is_active?: boolean;
}

export interface RoutinePatchRequest {
  template_title?: string;
  cron_schedule?: string;
  timezone?: string;
  is_active?: boolean;
}

export interface AlarmResponse {
  id: string;
  task_id: string;
  trigger_time: string;
  is_active: boolean;
  is_deleted: boolean;
  server_version: number;
}

export interface AlarmUpsertRequest {
  id?: string;
  task_id: string;
  trigger_time: string;
  is_active?: boolean;
}

export interface AlarmPatchRequest {
  trigger_time?: string;
  is_active?: boolean;
}

export interface PomodoroSessionResponse {
  id: string;
  task_id?: string | null;
  phase: PomodoroPhase;
  state: PomodoroSessionState;
  started_at: string;
  ends_at: string;
  completed_at?: string | null;
  duration_minutes: number;
  note?: string | null;
  task_update: PomodoroTaskUpdate;
  is_deleted: boolean;
  server_version: number;
}

export interface PomodoroSessionUpsertRequest {
  id?: string;
  task_id?: string | null;
  phase: PomodoroPhase;
  state: PomodoroSessionState;
  started_at: string;
  ends_at: string;
  completed_at?: string | null;
  duration_minutes: number;
  note?: string | null;
  task_update?: PomodoroTaskUpdate;
}

export interface PomodoroSessionPatchRequest {
  task_id?: string | null;
  phase?: PomodoroPhase;
  state?: PomodoroSessionState;
  started_at?: string;
  ends_at?: string;
  completed_at?: string | null;
  duration_minutes?: number;
  note?: string | null;
  task_update?: PomodoroTaskUpdate;
}

export class ApiError extends Error {
  readonly status: number;
  readonly details?: unknown;

  constructor(status: number, message: string, details?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function createAbunApiClient(options: ApiClientOptions = {}) {
  const baseUrl = (options.baseUrl ?? import.meta.env.VITE_ABUN_API_BASE_URL ?? '').replace(/\/$/, '');
  const fetchImpl = options.fetchImpl ?? fetch;
  const maxReadRetries = options.maxReadRetries ?? 2;

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const method = (init?.method ?? 'GET').toUpperCase();
    const isIdempotentRead = method === 'GET' || method === 'HEAD';
    const retries = isIdempotentRead ? maxReadRetries : 0;

    for (let attempt = 0; ; attempt += 1) {
      try {
        const headers = new Headers(init?.headers);
        headers.set('Accept', 'application/json');
        if (init?.body !== undefined) {
          headers.set('Content-Type', 'application/json');
        }
        if (options.bearerToken) {
          headers.set('Authorization', `Bearer ${options.bearerToken}`);
        }

        const response = await fetchImpl(`${baseUrl}${path}`, { ...init, headers });
        if (!response.ok) {
          let message = response.statusText;
          let details: unknown;
          try {
            details = await response.json();
            if (typeof details === 'object' && details !== null && 'message' in details) {
              message = String((details as { message?: string }).message ?? message);
            }
          } catch {
            // no-op
          }
          throw new ApiError(response.status, message, details);
        }

        if (response.status === 204) return undefined as T;
        return (await response.json()) as T;
      } catch (error) {
        if (error instanceof ApiError) throw error;
        if (attempt >= retries) throw error;
        await wait(150 * (attempt + 1));
      }
    }
  }

  return {
    requestOtp: (email: string) => request<void>('/auth/otp/request', { method: 'POST', body: JSON.stringify({ email }) }),
    verifyOtp: (email: string, otp: string) =>
      request<OtpVerifyResponse>('/auth/otp/verify', { method: 'POST', body: JSON.stringify({ email, otp }) }),

    listPreferences: () => request<PreferenceResponse[]>('/api/preferences'),
    getPreference: (key: string) => request<PreferenceResponse>(`/api/preferences/${encodeURIComponent(key)}`),
    putPreference: (key: string, value: string | null, value_type: PreferenceValueType) =>
      request<PreferenceResponse>(`/api/preferences/${encodeURIComponent(key)}`, {
        method: 'PUT',
        body: JSON.stringify({ value, value_type }),
      }),
    deletePreference: (key: string) => request<PreferenceResponse>(`/api/preferences/${encodeURIComponent(key)}`, { method: 'DELETE' }),

    getTask: (id: string) => request<TaskResponse>(`/api/tasks/${id}`),
    listTasks: () => request<TaskResponse[]>('/api/tasks'),
    createTask: (input: TaskUpsertRequest) => request<TaskResponse>('/api/tasks', { method: 'POST', body: JSON.stringify(input) }),
    updateTask: (id: string, input: TaskPatchRequest) => request<TaskResponse>(`/api/tasks/${id}`, { method: 'PATCH', body: JSON.stringify(input) }),
    deleteTask: (id: string) => request<TaskResponse>(`/api/tasks/${id}`, { method: 'DELETE' }),
    getTaskStatus: (id: string) => request<TaskStatusResponse>(`/api/tasks/${id}/status`),
    listTaskEvents: (id: string) => request<TaskEventResponse[]>(`/api/tasks/${id}/events`),
    createTaskEvent: (id: string, input: TaskEventCreateRequest) =>
      request<TaskEventResponse>(`/api/tasks/${id}/events`, { method: 'POST', body: JSON.stringify(input) }),

    listRoutines: () => request<RoutineResponse[]>('/api/routines'),
    getRoutine: (id: string) => request<RoutineResponse>(`/api/routines/${id}`),
    createRoutine: (input: RoutineUpsertRequest) => request<RoutineResponse>('/api/routines', { method: 'POST', body: JSON.stringify(input) }),
    updateRoutine: (id: string, input: RoutinePatchRequest) => request<RoutineResponse>(`/api/routines/${id}`, { method: 'PATCH', body: JSON.stringify(input) }),
    deleteRoutine: (id: string) => request<RoutineResponse>(`/api/routines/${id}`, { method: 'DELETE' }),

    listAlarms: () => request<AlarmResponse[]>('/api/alarms'),
    getAlarm: (id: string) => request<AlarmResponse>(`/api/alarms/${id}`),
    createAlarm: (input: AlarmUpsertRequest) => request<AlarmResponse>('/api/alarms', { method: 'POST', body: JSON.stringify(input) }),
    updateAlarm: (id: string, input: AlarmPatchRequest) => request<AlarmResponse>(`/api/alarms/${id}`, { method: 'PATCH', body: JSON.stringify(input) }),
    deleteAlarm: (id: string) => request<AlarmResponse>(`/api/alarms/${id}`, { method: 'DELETE' }),

    listPomodoroSessions: () => request<PomodoroSessionResponse[]>('/api/pomodoro-sessions'),
    getPomodoroSession: (id: string) => request<PomodoroSessionResponse>(`/api/pomodoro-sessions/${id}`),
    createPomodoroSession: (input: PomodoroSessionUpsertRequest) =>
      request<PomodoroSessionResponse>('/api/pomodoro-sessions', { method: 'POST', body: JSON.stringify(input) }),
    updatePomodoroSession: (id: string, input: PomodoroSessionPatchRequest) =>
      request<PomodoroSessionResponse>(`/api/pomodoro-sessions/${id}`, { method: 'PATCH', body: JSON.stringify(input) }),
    deletePomodoroSession: (id: string) => request<PomodoroSessionResponse>(`/api/pomodoro-sessions/${id}`, { method: 'DELETE' }),

    getJournal: (date: string) => request<TaskEventResponse[]>(`/api/journals/${encodeURIComponent(date)}`),
  };
}

export type AbunApiClient = ReturnType<typeof createAbunApiClient>;

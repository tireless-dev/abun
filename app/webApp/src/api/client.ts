export interface ApiClientOptions {
  baseUrl?: string;
  bearerToken?: string;
  fetchImpl?: typeof fetch;
}

export interface PreferenceResponse {
  key: string;
  value: string | null;
  value_type: 'STRING' | 'INT' | 'ENUM';
  is_deleted: boolean;
  server_version: number;
  server_updated_at?: string;
  created_at?: string;
}

export interface TaskResponse {
  id: string;
  parent_id?: string | null;
  routine_id?: string | null;
  title: string;
  is_deleted: boolean;
  server_version: number;
  server_updated_at?: string;
  created_at?: string;
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

export interface RoutineResponse {
  id: string;
  template_title: string;
  cron_schedule: string;
  timezone: string;
  is_active: boolean;
  is_deleted: boolean;
  server_version: number;
  server_updated_at?: string;
  created_at?: string;
}

export interface AlarmResponse {
  id: string;
  task_id: string;
  trigger_time: string;
  is_active: boolean;
  is_deleted: boolean;
  server_version: number;
  server_updated_at?: string;
  created_at?: string;
}

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export function createAbunApiClient(options: ApiClientOptions = {}) {
  const baseUrl = (options.baseUrl ?? '/api').replace(/\/$/, '');
  const fetchImpl = options.fetchImpl ?? fetch;

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const headers = new Headers(init?.headers);
    headers.set('Accept', 'application/json');
    if (init?.body !== undefined) {
      headers.set('Content-Type', 'application/json');
    }
    if (options.bearerToken) {
      headers.set('Authorization', `Bearer ${options.bearerToken}`);
    }
    const response = await fetchImpl(`${baseUrl}${path}`, {
      ...init,
      headers,
    });
    if (!response.ok) {
      let message = response.statusText;
      try {
        const body = (await response.json()) as { message?: string };
        message = body.message ?? message;
      } catch {
        // Keep the HTTP status text when the response is not JSON.
      }
      throw new ApiError(response.status, message);
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  return {
    listPreferences: () => request<PreferenceResponse[]>('/preferences'),
    getTask: (id: string) => request<TaskResponse>(`/tasks/${id}`),
    listTasks: () => request<TaskResponse[]>('/tasks'),
    createTask: (input: TaskUpsertRequest) =>
      request<TaskResponse>('/tasks', { method: 'POST', body: JSON.stringify(input) }),
    updateTask: (id: string, input: TaskPatchRequest) =>
      request<TaskResponse>(`/tasks/${id}`, { method: 'PATCH', body: JSON.stringify(input) }),
    deleteTask: (id: string) => request<TaskResponse>(`/tasks/${id}`, { method: 'DELETE' }),
    listRoutines: () => request<RoutineResponse[]>('/routines'),
    listAlarms: () => request<AlarmResponse[]>('/alarms'),
  };
}

export type AbunApiClient = ReturnType<typeof createAbunApiClient>;

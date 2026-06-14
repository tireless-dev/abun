import { useEffect, useMemo, useState } from 'react';
import {
  ApiError,
  type AlarmResponse,
  type AuthSessionResponse,
  type PomodoroPhase,
  type PomodoroSessionResponse,
  type PomodoroTaskUpdate,
  type PreferenceResponse,
  type RoutineResponse,
  type TaskEventResponse,
  type TaskEventType,
  type TaskResponse,
  type TaskStatus,
  createAbunApiClient,
} from '../api/client.ts';
import './app.css';

type MainTab = 'TODAY' | 'TASKS' | 'FOCUS' | 'JOURNAL' | 'PREFERENCES';
type TaskPanel = 'OPEN' | 'CLOSED' | 'ROUTINES' | 'ALARMS';
type ToastType = 'success' | 'error' | 'info';
type Toast = { id: number; type: ToastType; message: string };

type PendingDelete = {
  key: string;
  kind: 'task' | 'routine' | 'alarm';
  id: string;
  label: string;
  timerId: number;
  expiresAt: number;
};

type PreferencesState = {
  titlePrefix: string;
  defaultAlarmLeadMinutes: number;
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  timezoneOverride: string;
  dateFormat: 'ISO' | 'MONTH_DAY' | 'WEEKDAY_MONTH_DAY';
};

type AuthState = {
  mode: 'ANON' | 'OTP_SENT' | 'AUTH';
  email: string;
  otp: string;
  token: string;
  tokenExpiresAt: number;
  refreshToken: string;
  refreshTokenExpiresAt: number;
  userId: string;
};

type TaskWithStatus = TaskResponse & {
  status: TaskStatus;
  alarm?: AlarmResponse;
  latestEvent?: TaskEventResponse;
};

type StoredAuthSession = {
  accessToken: string;
  accessTokenExpiresAt: number;
  refreshToken: string;
  refreshTokenExpiresAt: number;
  userId: string;
};

const SESSION_KEY = 'abun_auth_session';
const UNDO_MS = 5000;
const ACCESS_TOKEN_REFRESH_WINDOW_MS = 60_000;

const DEFAULT_PREFS: PreferencesState = {
  titlePrefix: '',
  defaultAlarmLeadMinutes: 15,
  focusMinutes: 25,
  shortBreakMinutes: 5,
  longBreakMinutes: 15,
  timezoneOverride: 'SYSTEM',
  dateFormat: 'ISO',
};

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

function nowIso(): string {
  return new Date().toISOString();
}

function toIsoFromDateTimeLocal(value: string): string {
  return value ? new Date(value).toISOString() : '';
}

function toDateTimeLocalValue(date: Date): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function formatTime(value: string): string {
  return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatRemainingMs(ms: number): string {
  const sec = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(sec / 60).toString().padStart(2, '0');
  const s = (sec % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
}

function prefMap(preferences: PreferenceResponse[]): Record<string, PreferenceResponse> {
  const map: Record<string, PreferenceResponse> = {};
  for (const pref of preferences) if (!pref.is_deleted) map[pref.key] = pref;
  return map;
}

function parsePreferences(preferences: PreferenceResponse[]): PreferencesState {
  const p = prefMap(preferences);
  return {
    titlePrefix: p['task.title_prefix']?.value ?? '',
    defaultAlarmLeadMinutes: Number(p['task.default_alarm_lead_minutes']?.value ?? '15'),
    focusMinutes: Number(p['pomodoro.focus_minutes']?.value ?? '25'),
    shortBreakMinutes: Number(p['pomodoro.short_break_minutes']?.value ?? '5'),
    longBreakMinutes: Number(p['pomodoro.long_break_minutes']?.value ?? '15'),
    timezoneOverride: p['date.timezone_override']?.value ?? p['app.timezone_override']?.value ?? 'SYSTEM',
    dateFormat: ((p['date.format']?.value ?? p['app.date_format']?.value) as PreferencesState['dateFormat'] | undefined) ?? 'ISO',
  };
}

function statusRank(status: TaskStatus): number {
  if (status === 'IN_PROGRESS') return 0;
  if (status === 'PENDING') return 1;
  if (status === 'UNKNOWN') return 2;
  if (status === 'COMPLETED') return 3;
  return 4;
}

function eventRank(type: TaskEventType): number {
  if (type === 'PROGRESSED') return 0;
  if (type === 'ALARM_FIRED') return 1;
  if (type === 'CREATED') return 2;
  if (type === 'COMPLETED') return 3;
  return 4;
}

function statusLabel(status: TaskStatus): string {
  if (status === 'IN_PROGRESS') return 'In progress';
  if (status === 'PENDING') return 'Pending';
  if (status === 'COMPLETED') return 'Completed';
  if (status === 'CANCELLED') return 'Cancelled';
  return 'Unknown';
}

function eventLabel(type: TaskEventType): string {
  if (type === 'CREATED') return 'Created';
  if (type === 'PROGRESSED') return 'Progressed';
  if (type === 'COMPLETED') return 'Completed';
  if (type === 'CANCELLED') return 'Cancelled';
  return 'Alarm fired';
}

function phaseLabel(phase: PomodoroPhase): string {
  if (phase === 'SHORT_BREAK') return 'Short break';
  if (phase === 'LONG_BREAK') return 'Long break';
  return 'Focus';
}

function taskUpdateLabel(update: PomodoroTaskUpdate): string {
  if (update === 'PROGRESS') return 'Mark in progress';
  if (update === 'COMPLETE') return 'Complete task';
  if (update === 'CANCEL') return 'Cancel task';
  return 'No task change';
}

function isOpenStatus(status: TaskStatus): boolean {
  return status === 'PENDING' || status === 'IN_PROGRESS' || status === 'UNKNOWN';
}

function readStoredSession(): StoredAuthSession | null {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw) return null;

  try {
    const parsed = JSON.parse(raw) as Partial<StoredAuthSession>;
    if (
      typeof parsed.accessToken === 'string' &&
      typeof parsed.accessTokenExpiresAt === 'number' &&
      typeof parsed.refreshToken === 'string' &&
      typeof parsed.refreshTokenExpiresAt === 'number' &&
      typeof parsed.userId === 'string'
    ) {
      return parsed as StoredAuthSession;
    }
  } catch {
    // ignore invalid session payloads
  }

  localStorage.removeItem(SESSION_KEY);
  return null;
}

export function App() {
  const initialSession = readStoredSession();
  const [auth, setAuth] = useState<AuthState>({
    mode: initialSession ? 'AUTH' : 'ANON',
    email: '',
    otp: '',
    token: initialSession?.accessToken ?? '',
    tokenExpiresAt: initialSession?.accessTokenExpiresAt ?? 0,
    refreshToken: initialSession?.refreshToken ?? '',
    refreshTokenExpiresAt: initialSession?.refreshTokenExpiresAt ?? 0,
    userId: initialSession?.userId ?? '',
  });

  const [loading, setLoading] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [pendingDeletes, setPendingDeletes] = useState<PendingDelete[]>([]);
  const [selectedDate, setSelectedDate] = useState(todayIsoDate());
  const [mainTab, setMainTab] = useState<MainTab>('TODAY');
  const [taskPanel, setTaskPanel] = useState<TaskPanel>('OPEN');

  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [taskStatuses, setTaskStatuses] = useState<Record<string, TaskStatus>>({});
  const [taskEvents, setTaskEvents] = useState<Record<string, TaskEventResponse[]>>({});
  const [routines, setRoutines] = useState<RoutineResponse[]>([]);
  const [alarms, setAlarms] = useState<AlarmResponse[]>([]);
  const [pomodoroSessions, setPomodoroSessions] = useState<PomodoroSessionResponse[]>([]);
  const [journalEntries, setJournalEntries] = useState<Array<TaskEventResponse & { taskTitle: string }>>([]);
  const [preferences, setPreferences] = useState<PreferencesState>(DEFAULT_PREFS);

  const [taskDraft, setTaskDraft] = useState('');
  const [noteDrafts, setNoteDrafts] = useState<Record<string, string>>({});
  const [routineDraft, setRoutineDraft] = useState({ template_title: '', cron_schedule: '0 9 * * *', timezone: 'UTC' });
  const [alarmDraft, setAlarmDraft] = useState({ task_id: '', trigger_time: toDateTimeLocalValue(new Date(Date.now() + DEFAULT_PREFS.defaultAlarmLeadMinutes * 60_000)) });
  const [pomodoroDraft, setPomodoroDraft] = useState<{ taskId: string; phase: PomodoroPhase; note: string; taskUpdate: PomodoroTaskUpdate }>({
    taskId: '',
    phase: 'FOCUS',
    note: '',
    taskUpdate: 'NONE',
  });

  const authClient = useMemo(() => createAbunApiClient(), []);
  const client = useMemo(
    () =>
      createAbunApiClient({
        getBearerToken: async () => auth.token || undefined,
        refreshBearerToken: async () => await refreshAccessToken(),
      }),
    [auth.token, auth.refreshToken, auth.tokenExpiresAt, auth.refreshTokenExpiresAt],
  );

  function pushToast(type: ToastType, message: string) {
    const id = Date.now() + Math.floor(Math.random() * 1000);
    setToasts((prev) => [...prev, { id, type, message }]);
    window.setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 3000);
  }

  async function runAction(action: () => Promise<void>, successMessage?: string) {
    setWorking(true);
    setError(null);
    try {
      await action();
      if (successMessage) pushToast('success', successMessage);
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Action failed';
      setError(message);
      pushToast('error', message);
    } finally {
      setWorking(false);
    }
  }

  async function loadAll() {
    if (!auth.token && !auth.refreshToken) return;
    setLoading(true);
    setError(null);
    try {
      const [taskList, routineList, alarmList, sessionList, preferenceList, journal] = await Promise.all([
        client.listTasks(),
        client.listRoutines(),
        client.listAlarms(),
        client.listPomodoroSessions(),
        client.listPreferences(),
        client.getJournal(selectedDate),
      ]);

      const filteredTasks = taskList.filter((t) => !t.is_deleted);
      const visibleAlarms = alarmList.filter((a) => !a.is_deleted);
      const eventsByTask: Record<string, TaskEventResponse[]> = {};
      const statuses: Record<string, TaskStatus> = {};

      await Promise.all(filteredTasks.map(async (task) => {
        const events = await client.listTaskEvents(task.id).catch(() => []);
        eventsByTask[task.id] = events.filter((event) => !event.is_deleted);
        statuses[task.id] = await client.getTaskStatus(task.id).then((result) => result.status).catch(() => 'UNKNOWN' as TaskStatus);
      }));

      const byId = Object.fromEntries(filteredTasks.map((t) => [t.id, t]));
      setTasks(filteredTasks);
      setTaskStatuses(statuses);
      setTaskEvents(eventsByTask);
      setRoutines(routineList.filter((r) => !r.is_deleted));
      setAlarms(visibleAlarms);
      setPomodoroSessions(sessionList.filter((s) => !s.is_deleted));
      setPreferences(parsePreferences(preferenceList));
      setJournalEntries(
        journal
          .filter((e) => !e.is_deleted)
          .map((entry) => ({ ...entry, taskTitle: byId[entry.task_id]?.title ?? entry.task_id }))
          .sort((a, b) => (a.event_time < b.event_time ? 1 : -1)),
      );
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        clearAuthSession();
      }
      const message = e instanceof Error ? e.message : 'Failed loading data';
      setError(message);
      pushToast('error', message);
    } finally {
      setLoading(false);
    }
  }

  function clearAuthSession() {
    pendingDeletes.forEach((d) => window.clearTimeout(d.timerId));
    setPendingDeletes([]);
    localStorage.removeItem(SESSION_KEY);
    setAuth((prev) => ({
      ...prev,
      mode: 'ANON',
      otp: '',
      token: '',
      tokenExpiresAt: 0,
      refreshToken: '',
      refreshTokenExpiresAt: 0,
      userId: '',
    }));
    setTasks([]);
    setRoutines([]);
    setAlarms([]);
    setPomodoroSessions([]);
    setJournalEntries([]);
  }

  async function logout() {
    const refreshToken = auth.refreshToken;
    const accessToken = auth.token;
    clearAuthSession();
    if (refreshToken) {
      try {
        await authClient.logout(refreshToken, accessToken || undefined);
      } catch {
        // local logout still wins
      }
    }
    pushToast('info', 'Logged out');
  }

  async function refreshAccessToken(force = false): Promise<string | undefined> {
    if (!auth.refreshToken) {
      return auth.token || undefined;
    }

    const now = Date.now();
    if (!force && auth.token && auth.tokenExpiresAt - ACCESS_TOKEN_REFRESH_WINDOW_MS > now) {
      return auth.token;
    }

    if (auth.refreshTokenExpiresAt <= now) {
      clearAuthSession();
      return undefined;
    }

    try {
      const response = await authClient.refreshSession(auth.refreshToken);
      persistAuthSession(response);
      return response.access_token;
    } catch {
      clearAuthSession();
      return undefined;
    }
  }

  useEffect(() => () => pendingDeletes.forEach((d) => window.clearTimeout(d.timerId)), [pendingDeletes]);

  useEffect(() => {
    void loadAll();
  }, [auth.token, auth.refreshToken, selectedDate]);

  async function requestOtp() {
    await runAction(async () => {
      const email = auth.email.trim();
      if (!email) throw new Error('Email is required');
      await client.requestOtp(email);
      setAuth((prev) => ({ ...prev, mode: 'OTP_SENT' }));
    }, 'OTP sent');
  }

  async function verifyOtp() {
    await runAction(async () => {
      const email = auth.email.trim();
      const otp = auth.otp.trim();
      if (!email || !otp) throw new Error('Email and OTP are required');
      const response = await client.verifyOtp(email, otp);
      persistAuthSession(response);
    }, 'Logged in');
  }

  function persistAuthSession(response: AuthSessionResponse) {
    const nextSession = {
      accessToken: response.access_token,
      accessTokenExpiresAt: Date.parse(response.access_token_expires_at),
      refreshToken: response.refresh_token,
      refreshTokenExpiresAt: Date.parse(response.refresh_token_expires_at),
      userId: response.user_id,
    } satisfies StoredAuthSession;
    localStorage.setItem(SESSION_KEY, JSON.stringify(nextSession));
    setAuth((prev) => ({
      ...prev,
      mode: 'AUTH',
      otp: '',
      token: nextSession.accessToken,
      tokenExpiresAt: nextSession.accessTokenExpiresAt,
      refreshToken: nextSession.refreshToken,
      refreshTokenExpiresAt: nextSession.refreshTokenExpiresAt,
      userId: nextSession.userId,
    }));
  }

  async function appendEvent(taskId: string, type: TaskEventType, content?: string) {
    await client.createTaskEvent(taskId, { journal_date: selectedDate, event_type: type, event_time: nowIso(), content: content?.trim() || null });
    setNoteDrafts((prev) => ({ ...prev, [taskId]: '' }));
    await loadAll();
  }

  async function createTask() {
    const rawTitle = taskDraft.trim();
    if (!rawTitle) return;
    const title = `${preferences.titlePrefix} ${rawTitle}`.trim();
    await client.createTask({ title, journal_date: selectedDate, event_time: nowIso() });
    setTaskDraft('');
    await loadAll();
  }

  async function startTimer(taskId: string | null, phase: PomodoroPhase = 'FOCUS') {
    const now = new Date();
    const duration = phase === 'FOCUS' ? preferences.focusMinutes : phase === 'SHORT_BREAK' ? preferences.shortBreakMinutes : preferences.longBreakMinutes;
    const ends = new Date(now.getTime() + duration * 60_000);
    await client.createPomodoroSession({
      task_id: taskId,
      phase,
      state: 'ACTIVE',
      started_at: now.toISOString(),
      ends_at: ends.toISOString(),
      duration_minutes: duration,
      task_update: 'NONE',
    });
    setMainTab('FOCUS');
    await loadAll();
  }

  async function finishPomodoro(sessionId: string, cancelled: boolean) {
    const active = pomodoroSessions.find((s) => s.id === sessionId);
    if (!active) return;
    await client.updatePomodoroSession(sessionId, {
      state: cancelled ? 'CANCELLED' : 'COMPLETED',
      completed_at: nowIso(),
      note: pomodoroDraft.note || null,
      task_update: cancelled ? 'NONE' : pomodoroDraft.taskUpdate,
    });
    if (!cancelled && active.task_id && pomodoroDraft.taskUpdate === 'PROGRESS') await appendEvent(active.task_id, 'PROGRESSED', pomodoroDraft.note);
    if (!cancelled && active.task_id && pomodoroDraft.taskUpdate === 'COMPLETE') await appendEvent(active.task_id, 'COMPLETED', pomodoroDraft.note);
    if (!cancelled && active.task_id && pomodoroDraft.taskUpdate === 'CANCEL') await appendEvent(active.task_id, 'CANCELLED', pomodoroDraft.note);
    setPomodoroDraft((prev) => ({ ...prev, note: '', taskUpdate: 'NONE' }));
    await loadAll();
  }

  function scheduleDelete(kind: PendingDelete['kind'], id: string, label: string) {
    const key = `${kind}:${id}`;
    const expiresAt = Date.now() + UNDO_MS;
    const timerId = window.setTimeout(async () => {
      try {
        if (kind === 'task') await client.deleteTask(id);
        if (kind === 'routine') await client.deleteRoutine(id);
        if (kind === 'alarm') await client.deleteAlarm(id);
        setPendingDeletes((prev) => prev.filter((item) => item.key !== key));
        await loadAll();
      } catch (e) {
        setPendingDeletes((prev) => prev.filter((item) => item.key !== key));
        pushToast('error', e instanceof Error ? e.message : 'Delete failed');
      }
    }, UNDO_MS);

    setPendingDeletes((prev) => [...prev, { key, kind, id, label, timerId, expiresAt }]);
    pushToast('info', `${label} removed. Undo available`);
  }

  function undoDelete(key: string) {
    const item = pendingDeletes.find((d) => d.key === key);
    if (!item) return;
    window.clearTimeout(item.timerId);
    setPendingDeletes((prev) => prev.filter((d) => d.key !== key));
    pushToast('success', `${item.label} restored`);
  }

  const nowMs = Date.now();
  const activePomodoro = pomodoroSessions.find((s) => s.state === 'ACTIVE');
  const latestAlarmByTask = useMemo(() => {
    const map: Record<string, AlarmResponse> = {};
    alarms
      .filter((alarm) => alarm.is_active)
      .sort((a, b) => new Date(a.trigger_time).getTime() - new Date(b.trigger_time).getTime())
      .forEach((alarm) => {
        map[alarm.task_id] ??= alarm;
      });
    return map;
  }, [alarms]);

  const tasksWithStatus: TaskWithStatus[] = useMemo(() => tasks
    .map((task) => {
      const events = taskEvents[task.id] ?? [];
      const latestEvent = events.slice().sort((a, b) => (a.event_time < b.event_time ? 1 : -1))[0];
      return {
        ...task,
        status: (taskStatuses[task.id] ?? 'UNKNOWN') as TaskStatus,
        alarm: latestAlarmByTask[task.id],
        latestEvent,
      };
    })
    .sort((a, b) => statusRank(a.status) - statusRank(b.status) || a.title.localeCompare(b.title)), [latestAlarmByTask, taskEvents, taskStatuses, tasks]);

  const openTasks = tasksWithStatus.filter((task) => isOpenStatus(task.status));
  const closedTasks = tasksWithStatus.filter((task) => !isOpenStatus(task.status));
  const inProgress = tasksWithStatus.filter((task) => task.status === 'IN_PROGRESS');
  const upcomingAlarms = alarms
    .filter((alarm) => alarm.is_active)
    .sort((a, b) => new Date(a.trigger_time).getTime() - new Date(b.trigger_time).getTime());
  const dueNow = upcomingAlarms.filter((alarm) => new Date(alarm.trigger_time).getTime() <= nowMs);

  if (auth.mode !== 'AUTH') {
    return (
      <main className="auth-shell">
        <section className="auth-panel">
          <p className="brand-mark">abun</p>
          <h1>Sign in</h1>
          <p className="muted">Login with email OTP to access your cloud workspace.</p>
          <input aria-label="Email" placeholder="Email" value={auth.email} onChange={(e) => setAuth((p) => ({ ...p, email: e.target.value }))} />
          {auth.mode === 'OTP_SENT' && <input aria-label="OTP" placeholder="OTP code" value={auth.otp} onChange={(e) => setAuth((p) => ({ ...p, otp: e.target.value }))} />}
          {auth.mode === 'ANON' ? (
            <button className="primary-action" disabled={working} onClick={() => void requestOtp()}>Send OTP</button>
          ) : (
            <button className="primary-action" disabled={working} onClick={() => void verifyOtp()}>Verify OTP</button>
          )}
          {error && <p className="error">{error}</p>}
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div>
          <p className="brand-mark">abun</p>
          <nav className="nav-list" aria-label="Main tabs">
            {(['TODAY', 'TASKS', 'FOCUS', 'JOURNAL', 'PREFERENCES'] as MainTab[]).map((tab) => (
              <button key={tab} className={mainTab === tab ? 'active' : ''} onClick={() => setMainTab(tab)}>
                {tab === 'PREFERENCES' ? 'Settings' : tab[0] + tab.slice(1).toLowerCase()}
              </button>
            ))}
          </nav>
        </div>
        <div className="sidebar-footer">
          <button disabled={loading || working} onClick={() => void loadAll()}>{loading ? 'Syncing' : 'Sync'}</button>
          <button disabled={working} onClick={logout}>Logout</button>
        </div>
      </aside>

      <section className="workspace">
        <header className="workspace-header">
          <div>
            <p className="eyebrow">{selectedDate}</p>
            <h1>{mainTab === 'PREFERENCES' ? 'Settings' : mainTab[0] + mainTab.slice(1).toLowerCase()}</h1>
          </div>
          <div className="quick-capture">
            <input aria-label="Task title" placeholder="Capture task" value={taskDraft} onChange={(e) => setTaskDraft(e.target.value)} onKeyDown={(e) => {
              if (e.key === 'Enter') void runAction(createTask, 'Task created');
            }} />
            <button className="primary-action" disabled={working || !taskDraft.trim()} onClick={() => void runAction(createTask, 'Task created')}>Add</button>
          </div>
        </header>

        {error && <p className="error banner">{error}</p>}

        {pendingDeletes.length > 0 && (
          <section className="pending-strip">
            {pendingDeletes.map((d) => (
              <div key={d.key} className="pending-item">
                <span>{d.label} deletes in {Math.max(1, Math.ceil((d.expiresAt - Date.now()) / 1000.0))}s</span>
                <button onClick={() => undoDelete(d.key)}>Undo</button>
              </div>
            ))}
          </section>
        )}

        {mainTab === 'TODAY' && (
          <section className="page-grid today-grid">
            <section className="panel command-panel">
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">Daily desk</p>
                  <h2>Today</h2>
                </div>
                <input className="date-input" type="date" value={selectedDate} onChange={(e) => setSelectedDate(e.target.value)} />
              </div>
              <div className="metric-row">
                <Metric label="Open" value={openTasks.length} />
                <Metric label="In focus" value={inProgress.length} />
                <Metric label="Due" value={dueNow.length} />
              </div>
              <TaskStack tasks={openTasks.slice(0, 6)} empty="No open tasks." onEvent={appendEvent} onDelete={scheduleDelete} onFocus={(taskId) => startTimer(taskId)} noteDrafts={noteDrafts} setNoteDrafts={setNoteDrafts} working={working} compact />
            </section>

            <section className="panel focus-strip">
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">Focus</p>
                  <h2>{activePomodoro ? formatRemainingMs(new Date(activePomodoro.ends_at).getTime() - nowMs) : 'Ready'}</h2>
                </div>
                <button disabled={working || !!activePomodoro} onClick={() => void runAction(() => startTimer(openTasks[0]?.id ?? null), 'Timer started')}>Start</button>
              </div>
              <p className="muted">{activePomodoro ? tasks.find((t) => t.id === activePomodoro.task_id)?.title ?? 'Solo focus' : 'No active timer'}</p>
            </section>

            <section className="panel">
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">Agenda</p>
                  <h2>Alarms</h2>
                </div>
              </div>
              <AlarmList alarms={upcomingAlarms.slice(0, 8)} tasks={tasks} empty="No active alarms." />
            </section>

            <section className="panel">
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">Ledger</p>
                  <h2>Journal</h2>
                </div>
                <button onClick={() => setMainTab('JOURNAL')}>Open</button>
              </div>
              <JournalList entries={journalEntries.slice(0, 8)} />
            </section>
          </section>
        )}

        {mainTab === 'TASKS' && (
          <section className="task-layout">
            <nav className="segmented" aria-label="Task panels">
              {(['OPEN', 'CLOSED', 'ROUTINES', 'ALARMS'] as TaskPanel[]).map((panel) => (
                <button key={panel} className={taskPanel === panel ? 'active' : ''} onClick={() => setTaskPanel(panel)}>
                  {panel[0] + panel.slice(1).toLowerCase()}
                </button>
              ))}
            </nav>

            {taskPanel === 'OPEN' && (
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <p className="eyebrow">Ledger-derived</p>
                    <h2>Open tasks</h2>
                  </div>
                </div>
                <TaskStack tasks={openTasks} empty="No open tasks." onEvent={appendEvent} onDelete={scheduleDelete} onFocus={(taskId) => startTimer(taskId)} noteDrafts={noteDrafts} setNoteDrafts={setNoteDrafts} working={working} />
              </section>
            )}

            {taskPanel === 'CLOSED' && (
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <p className="eyebrow">Archive</p>
                    <h2>Closed tasks</h2>
                  </div>
                </div>
                <TaskStack tasks={closedTasks} empty="No closed tasks." onEvent={appendEvent} onDelete={scheduleDelete} onFocus={(taskId) => startTimer(taskId)} noteDrafts={noteDrafts} setNoteDrafts={setNoteDrafts} working={working} />
              </section>
            )}

            {taskPanel === 'ROUTINES' && (
              <section className="two-column">
                <section className="panel">
                  <div className="panel-heading"><h2>Create routine</h2></div>
                  <input value={routineDraft.template_title} onChange={(e) => setRoutineDraft((p) => ({ ...p, template_title: e.target.value }))} placeholder="Routine title" />
                  <input value={routineDraft.cron_schedule} onChange={(e) => setRoutineDraft((p) => ({ ...p, cron_schedule: e.target.value }))} placeholder="Cron" />
                  <input value={routineDraft.timezone} onChange={(e) => setRoutineDraft((p) => ({ ...p, timezone: e.target.value }))} placeholder="Timezone" />
                  <button className="primary-action" disabled={working || !routineDraft.template_title || !routineDraft.cron_schedule || !routineDraft.timezone} onClick={() => void runAction(async () => {
                    await client.createRoutine(routineDraft);
                    setRoutineDraft((p) => ({ ...p, template_title: '' }));
                    await loadAll();
                  }, 'Routine created')}>Create</button>
                </section>
                <section className="panel">
                  <div className="panel-heading"><h2>Routines</h2></div>
                  {routines.length === 0 ? <p className="empty">No routines.</p> : routines.map((routine) => (
                    <div key={routine.id} className="list-row">
                      <div className="row-main">
                        <strong>{routine.template_title}</strong>
                        <span>{routine.cron_schedule} - {routine.timezone}</span>
                      </div>
                      <div className="row-actions">
                        <span className={`status-pill ${routine.is_active ? 'pending' : 'unknown'}`}>{routine.is_active ? 'Active' : 'Paused'}</span>
                        <button disabled={working} onClick={() => void runAction(async () => { await client.updateRoutine(routine.id, { is_active: !routine.is_active }); await loadAll(); }, 'Routine updated')}>{routine.is_active ? 'Pause' : 'Activate'}</button>
                        <button disabled={working} onClick={() => scheduleDelete('routine', routine.id, `Routine "${routine.template_title}"`)}>Delete</button>
                      </div>
                    </div>
                  ))}
                </section>
              </section>
            )}

            {taskPanel === 'ALARMS' && (
              <section className="two-column">
                <section className="panel">
                  <div className="panel-heading"><h2>Create alarm</h2></div>
                  <select value={alarmDraft.task_id} onChange={(e) => setAlarmDraft((p) => ({ ...p, task_id: e.target.value }))}>
                    <option value="">Select task</option>
                    {openTasks.map((t) => <option key={t.id} value={t.id}>{t.title}</option>)}
                  </select>
                  <input type="datetime-local" value={alarmDraft.trigger_time} onChange={(e) => setAlarmDraft((p) => ({ ...p, trigger_time: e.target.value }))} />
                  <button className="primary-action" disabled={working || !alarmDraft.task_id || !alarmDraft.trigger_time} onClick={() => void runAction(async () => {
                    await client.createAlarm({ task_id: alarmDraft.task_id, trigger_time: toIsoFromDateTimeLocal(alarmDraft.trigger_time), is_active: true });
                    setAlarmDraft((p) => ({ ...p, trigger_time: '' }));
                    await loadAll();
                  }, 'Alarm created')}>Create</button>
                </section>
                <section className="panel">
                  <div className="panel-heading"><h2>Alarms</h2></div>
                  <AlarmManager alarms={alarms} tasks={tasks} working={working} onToggle={(alarm) => runAction(async () => { await client.updateAlarm(alarm.id, { is_active: !alarm.is_active }); await loadAll(); }, 'Alarm updated')} onDelete={(alarm, title) => scheduleDelete('alarm', alarm.id, `Alarm for ${title}`)} />
                </section>
              </section>
            )}
          </section>
        )}

        {mainTab === 'FOCUS' && (
          <section className="two-column">
            <section className="panel timer-panel">
              <p className="eyebrow">Timer</p>
              <h2>{activePomodoro ? formatRemainingMs(new Date(activePomodoro.ends_at).getTime() - nowMs) : '00:00'}</h2>
              <p className="muted">{activePomodoro ? tasks.find((t) => t.id === activePomodoro.task_id)?.title ?? 'Solo focus' : 'No active timer'}</p>
              {!activePomodoro && (
                <>
                  <select value={pomodoroDraft.taskId} onChange={(e) => setPomodoroDraft((p) => ({ ...p, taskId: e.target.value }))}>
                    <option value="">Solo focus</option>
                    {openTasks.map((t) => <option key={t.id} value={t.id}>{t.title}</option>)}
                  </select>
                  <select value={pomodoroDraft.phase} onChange={(e) => setPomodoroDraft((p) => ({ ...p, phase: e.target.value as PomodoroPhase }))}>
                    <option value="FOCUS">Focus</option>
                    <option value="SHORT_BREAK">Short break</option>
                    <option value="LONG_BREAK">Long break</option>
                  </select>
                  <button className="primary-action" disabled={working} onClick={() => void runAction(() => startTimer(pomodoroDraft.taskId || null, pomodoroDraft.phase), 'Timer started')}>Start</button>
                </>
              )}
              {activePomodoro && (
                <>
                  <input value={pomodoroDraft.note} onChange={(e) => setPomodoroDraft((p) => ({ ...p, note: e.target.value }))} placeholder="Session note" />
                  <select value={pomodoroDraft.taskUpdate} onChange={(e) => setPomodoroDraft((p) => ({ ...p, taskUpdate: e.target.value as PomodoroTaskUpdate }))}>
                    {(['NONE', 'PROGRESS', 'COMPLETE', 'CANCEL'] as PomodoroTaskUpdate[]).map((update) => <option key={update} value={update}>{taskUpdateLabel(update)}</option>)}
                  </select>
                  <div className="button-row">
                    <button className="primary-action" disabled={working} onClick={() => void runAction(() => finishPomodoro(activePomodoro.id, false), 'Timer completed')}>Complete</button>
                    <button disabled={working} onClick={() => void runAction(() => finishPomodoro(activePomodoro.id, true), 'Timer cancelled')}>Stop</button>
                  </div>
                </>
              )}
            </section>
            <section className="panel">
              <div className="panel-heading"><h2>Recent sessions</h2></div>
              {pomodoroSessions.length === 0 ? <p className="empty">No sessions.</p> : pomodoroSessions.slice().sort((a, b) => (a.started_at < b.started_at ? 1 : -1)).slice(0, 10).map((s) => (
                <div key={s.id} className="list-row">
                  <div className="row-main">
                    <strong>{phaseLabel(s.phase)}</strong>
                    <span>{tasks.find((t) => t.id === s.task_id)?.title ?? 'Solo focus'} - {formatDateTime(s.started_at)}</span>
                  </div>
                  <span className={`status-pill ${s.state === 'COMPLETED' ? 'completed' : s.state === 'ACTIVE' ? 'progress' : 'cancelled'}`}>{s.state.toLowerCase()}</span>
                </div>
              ))}
            </section>
          </section>
        )}

        {mainTab === 'JOURNAL' && (
          <section className="panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Append-only</p>
                <h2>Journal</h2>
              </div>
              <input className="date-input" type="date" value={selectedDate} onChange={(e) => setSelectedDate(e.target.value)} />
            </div>
            <JournalList entries={journalEntries} />
          </section>
        )}

        {mainTab === 'PREFERENCES' && (
          <section className="panel settings-panel">
            <div className="panel-heading"><h2>Preferences</h2></div>
            <label>Title prefix</label>
            <input value={preferences.titlePrefix} onChange={(e) => setPreferences((p) => ({ ...p, titlePrefix: e.target.value }))} />
            <label>Default alarm lead minutes</label>
            <input type="number" min={0} value={preferences.defaultAlarmLeadMinutes} onChange={(e) => setPreferences((p) => ({ ...p, defaultAlarmLeadMinutes: Number(e.target.value) }))} />
            <label>Focus minutes</label>
            <input type="number" min={1} value={preferences.focusMinutes} onChange={(e) => setPreferences((p) => ({ ...p, focusMinutes: Number(e.target.value) }))} />
            <label>Short break minutes</label>
            <input type="number" min={1} value={preferences.shortBreakMinutes} onChange={(e) => setPreferences((p) => ({ ...p, shortBreakMinutes: Number(e.target.value) }))} />
            <label>Long break minutes</label>
            <input type="number" min={1} value={preferences.longBreakMinutes} onChange={(e) => setPreferences((p) => ({ ...p, longBreakMinutes: Number(e.target.value) }))} />
            <label>Timezone override</label>
            <input value={preferences.timezoneOverride} onChange={(e) => setPreferences((p) => ({ ...p, timezoneOverride: e.target.value }))} />
            <label>Date format</label>
            <select value={preferences.dateFormat} onChange={(e) => setPreferences((p) => ({ ...p, dateFormat: e.target.value as PreferencesState['dateFormat'] }))}>
              <option value="ISO">ISO</option>
              <option value="MONTH_DAY">Month day</option>
              <option value="WEEKDAY_MONTH_DAY">Weekday</option>
            </select>
            <button className="primary-action" disabled={working} onClick={() => void runAction(async () => {
              await Promise.all([
                client.putPreference('task.title_prefix', preferences.titlePrefix, 'STRING'),
                client.putPreference('task.default_alarm_lead_minutes', String(preferences.defaultAlarmLeadMinutes), 'INT'),
                client.putPreference('pomodoro.focus_minutes', String(preferences.focusMinutes), 'INT'),
                client.putPreference('pomodoro.short_break_minutes', String(preferences.shortBreakMinutes), 'INT'),
                client.putPreference('pomodoro.long_break_minutes', String(preferences.longBreakMinutes), 'INT'),
                client.putPreference('date.timezone_override', preferences.timezoneOverride, 'STRING'),
                client.putPreference('date.format', preferences.dateFormat, 'ENUM'),
              ]);
              await loadAll();
            }, 'Preferences saved')}>Save</button>
          </section>
        )}
      </section>

      <section className="toast-stack" aria-live="polite" aria-atomic="true">
        {toasts.map((toast) => <p key={toast.id} className={`toast ${toast.type}`}>{toast.message}</p>)}
      </section>
    </main>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="metric">
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

function TaskStack({
  tasks,
  empty,
  onEvent,
  onDelete,
  onFocus,
  noteDrafts,
  setNoteDrafts,
  working,
  compact = false,
}: {
  tasks: TaskWithStatus[];
  empty: string;
  onEvent: (taskId: string, type: TaskEventType, content?: string) => Promise<void>;
  onDelete: (kind: 'task', id: string, label: string) => void;
  onFocus: (taskId: string) => Promise<void>;
  noteDrafts: Record<string, string>;
  setNoteDrafts: React.Dispatch<React.SetStateAction<Record<string, string>>>;
  working: boolean;
  compact?: boolean;
}) {
  if (tasks.length === 0) return <p className="empty">{empty}</p>;
  return (
    <div className="task-stack">
      {tasks.map((task) => (
        <article key={task.id} className={`task-row ${compact ? 'compact' : ''}`}>
          <div className="task-title-line">
            <div>
              <strong>{task.title}</strong>
              <div className="task-meta">
                <span className={`status-pill ${task.status.toLowerCase().replace('_', '-')}`}>{statusLabel(task.status)}</span>
                {task.alarm && <span>{formatDateTime(task.alarm.trigger_time)}</span>}
                {task.latestEvent && <span>{eventLabel(task.latestEvent.event_type)}</span>}
              </div>
            </div>
            <div className="row-actions">
              {isOpenStatus(task.status) && <button disabled={working} onClick={() => void onFocus(task.id)}>Focus</button>}
              <button disabled={working} onClick={() => onDelete('task', task.id, `Task "${task.title}"`)}>Delete</button>
            </div>
          </div>
          {!compact && (
            <>
              <input value={noteDrafts[task.id] ?? ''} onChange={(e) => setNoteDrafts((prev) => ({ ...prev, [task.id]: e.target.value }))} placeholder="Journal note" />
              <div className="button-row">
                <button disabled={working} onClick={() => void onEvent(task.id, 'PROGRESSED', noteDrafts[task.id])}>Progress</button>
                <button disabled={working} onClick={() => void onEvent(task.id, 'COMPLETED', noteDrafts[task.id])}>Complete</button>
                <button disabled={working} onClick={() => void onEvent(task.id, 'CANCELLED', noteDrafts[task.id])}>Cancel</button>
              </div>
            </>
          )}
        </article>
      ))}
    </div>
  );
}

function AlarmList({ alarms, tasks, empty }: { alarms: AlarmResponse[]; tasks: TaskResponse[]; empty: string }) {
  if (alarms.length === 0) return <p className="empty">{empty}</p>;
  return (
    <div className="timeline-list">
      {alarms.map((alarm) => (
        <div key={alarm.id} className="timeline-item">
          <time>{formatTime(alarm.trigger_time)}</time>
          <span>{tasks.find((task) => task.id === alarm.task_id)?.title ?? alarm.task_id}</span>
        </div>
      ))}
    </div>
  );
}

function AlarmManager({
  alarms,
  tasks,
  working,
  onToggle,
  onDelete,
}: {
  alarms: AlarmResponse[];
  tasks: TaskResponse[];
  working: boolean;
  onToggle: (alarm: AlarmResponse) => Promise<void>;
  onDelete: (alarm: AlarmResponse, title: string) => void;
}) {
  if (alarms.length === 0) return <p className="empty">No alarms.</p>;
  return (
    <div className="task-stack">
      {alarms.slice().sort((a, b) => new Date(a.trigger_time).getTime() - new Date(b.trigger_time).getTime()).map((alarm) => {
        const title = tasks.find((task) => task.id === alarm.task_id)?.title ?? alarm.task_id;
        return (
          <div key={alarm.id} className="list-row">
            <div className="row-main">
              <strong>{title}</strong>
              <span>{formatDateTime(alarm.trigger_time)}</span>
            </div>
            <div className="row-actions">
              <span className={`status-pill ${alarm.is_active ? 'pending' : 'unknown'}`}>{alarm.is_active ? 'Active' : 'Paused'}</span>
              <button disabled={working} onClick={() => void onToggle(alarm)}>{alarm.is_active ? 'Pause' : 'Activate'}</button>
              <button disabled={working} onClick={() => onDelete(alarm, title)}>Delete</button>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function JournalList({ entries }: { entries: Array<TaskEventResponse & { taskTitle: string }> }) {
  if (entries.length === 0) return <p className="empty">No journal entries.</p>;
  return (
    <div className="journal-list">
      {entries.map((entry) => (
        <div key={entry.id} className="journal-row">
          <time>{formatTime(entry.event_time)}</time>
          <div>
            <strong>{entry.taskTitle}</strong>
            <span>{eventLabel(entry.event_type)}{entry.content ? ` - ${entry.content}` : ''}</span>
          </div>
          <span className={`event-dot rank-${eventRank(entry.event_type)}`} />
        </div>
      ))}
    </div>
  );
}

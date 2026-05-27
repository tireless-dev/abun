import { useEffect, useMemo, useState } from 'react';
import {
  ApiError,
  type AlarmResponse,
  type PomodoroPhase,
  type PomodoroSessionResponse,
  type PomodoroTaskUpdate,
  type PreferenceResponse,
  type RoutineResponse,
  type TaskEventType,
  type TaskResponse,
  type TaskStatus,
  createAbunApiClient,
} from '../api/client.ts';
import './app.css';

type MainTab = 'TODAY' | 'TASKS' | 'PREFERENCES';
type TaskTab = 'TASKS' | 'ALARMS';
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
  userId: string;
};

const TOKEN_KEY = 'abun_access_token';
const USER_KEY = 'abun_user_id';
const UNDO_MS = 5000;

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
    timezoneOverride: p['date.timezone_override']?.value ?? 'SYSTEM',
    dateFormat: (p['date.format']?.value as PreferencesState['dateFormat'] | undefined) ?? 'ISO',
  };
}

function statusPriority(status: TaskStatus): number {
  if (status === 'IN_PROGRESS') return 0;
  if (status === 'TODO') return 1;
  if (status === 'COMPLETED') return 2;
  return 3;
}

function toIsoFromDateTimeLocal(value: string): string {
  return value ? new Date(value).toISOString() : '';
}

export function App() {
  const [auth, setAuth] = useState<AuthState>({
    mode: localStorage.getItem(TOKEN_KEY) ? 'AUTH' : 'ANON',
    email: '',
    otp: '',
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    userId: localStorage.getItem(USER_KEY) ?? '',
  });

  const [loading, setLoading] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [pendingDeletes, setPendingDeletes] = useState<PendingDelete[]>([]);
  const [selectedDate, setSelectedDate] = useState(todayIsoDate());
  const [mainTab, setMainTab] = useState<MainTab>('TODAY');
  const [taskTab, setTaskTab] = useState<TaskTab>('TASKS');

  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [taskStatuses, setTaskStatuses] = useState<Record<string, TaskStatus>>({});
  const [routines, setRoutines] = useState<RoutineResponse[]>([]);
  const [alarms, setAlarms] = useState<AlarmResponse[]>([]);
  const [pomodoroSessions, setPomodoroSessions] = useState<PomodoroSessionResponse[]>([]);
  const [journalEntries, setJournalEntries] = useState<Array<{ taskTitle: string; eventType: TaskEventType; eventTime: string; content?: string | null }>>([]);
  const [preferences, setPreferences] = useState<PreferencesState>(DEFAULT_PREFS);

  const [taskDraft, setTaskDraft] = useState('');
  const [routineDraft, setRoutineDraft] = useState({ template_title: '', cron_schedule: '0 9 * * *', timezone: 'UTC' });
  const [alarmDraft, setAlarmDraft] = useState({ task_id: '', trigger_time: '' });
  const [pomodoroDraft, setPomodoroDraft] = useState<{ taskId: string; phase: PomodoroPhase; note: string; taskUpdate: PomodoroTaskUpdate }>({
    taskId: '', phase: 'FOCUS', note: '', taskUpdate: 'NONE',
  });

  const client = useMemo(() => createAbunApiClient({ bearerToken: auth.token || undefined }), [auth.token]);

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
    if (!auth.token) return;
    setLoading(true);
    setError(null);
    try {
      const [taskList, routineList, alarmList, sessionList, preferenceList, journal] = await Promise.all([
        client.listTasks(), client.listRoutines(), client.listAlarms(), client.listPomodoroSessions(), client.listPreferences(), client.getJournal(selectedDate),
      ]);

      const filteredTasks = taskList.filter((t) => !t.is_deleted);
      setTasks(filteredTasks);
      setRoutines(routineList.filter((r) => !r.is_deleted));
      setAlarms(alarmList.filter((a) => !a.is_deleted));
      setPomodoroSessions(sessionList.filter((s) => !s.is_deleted));
      setPreferences(parsePreferences(preferenceList));

      const statuses = await Promise.all(filteredTasks.map(async (task) => [task.id, (await client.getTaskStatus(task.id)).status] as const));
      setTaskStatuses(Object.fromEntries(statuses));

      const byId = Object.fromEntries(filteredTasks.map((t) => [t.id, t]));
      setJournalEntries(
        journal
          .filter((e) => !e.is_deleted)
          .map((entry) => ({ taskTitle: byId[entry.task_id]?.title ?? entry.task_id, eventType: entry.event_type, eventTime: entry.event_time, content: entry.content }))
          .sort((a, b) => (a.eventTime < b.eventTime ? 1 : -1)),
      );
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) logout();
      const message = e instanceof Error ? e.message : 'Failed loading data';
      setError(message);
      pushToast('error', message);
    } finally {
      setLoading(false);
    }
  }

  function logout() {
    pendingDeletes.forEach((d) => window.clearTimeout(d.timerId));
    setPendingDeletes([]);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setAuth({ mode: 'ANON', email: '', otp: '', token: '', userId: '' });
    setTasks([]); setRoutines([]); setAlarms([]); setPomodoroSessions([]); setJournalEntries([]);
    pushToast('info', 'Logged out');
  }

  useEffect(() => {
    return () => {
      pendingDeletes.forEach((d) => window.clearTimeout(d.timerId));
    };
  }, [pendingDeletes]);

  useEffect(() => {
    void loadAll();
  }, [auth.token, selectedDate]);

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
      localStorage.setItem(TOKEN_KEY, response.access_token);
      localStorage.setItem(USER_KEY, response.user_id);
      setAuth((prev) => ({ ...prev, mode: 'AUTH', token: response.access_token, userId: response.user_id, otp: '' }));
    }, 'Logged in');
  }

  async function appendEvent(taskId: string, type: TaskEventType, content?: string) {
    await client.createTaskEvent(taskId, { journal_date: selectedDate, event_type: type, event_time: nowIso(), content: content || null });
    await loadAll();
  }

  async function finishPomodoro(sessionId: string, cancelled: boolean) {
    const active = pomodoroSessions.find((s) => s.id === sessionId);
    if (!active) return;
    await client.updatePomodoroSession(sessionId, {
      state: cancelled ? 'CANCELLED' : 'COMPLETED',
      completed_at: nowIso(),
      note: pomodoroDraft.note || null,
      task_update: cancelled ? 'CANCEL' : pomodoroDraft.taskUpdate,
    });
    if (!cancelled && active.task_id && pomodoroDraft.taskUpdate === 'PROGRESS') await appendEvent(active.task_id, 'PROGRESSED', pomodoroDraft.note);
    if (!cancelled && active.task_id && pomodoroDraft.taskUpdate === 'COMPLETE') await appendEvent(active.task_id, 'COMPLETED', pomodoroDraft.note);
    if (cancelled && active.task_id) await appendEvent(active.task_id, 'CANCELLED', pomodoroDraft.note);
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
    pushToast('info', `${label} removed. Undo available for ${UNDO_MS / 1000}s`);
  }

  function undoDelete(key: string) {
    const item = pendingDeletes.find((d) => d.key === key);
    if (!item) return;
    window.clearTimeout(item.timerId);
    setPendingDeletes((prev) => prev.filter((d) => d.key !== key));
    pushToast('success', `${item.label} deletion cancelled`);
  }

  if (auth.mode !== 'AUTH') {
    return (
      <main className="app-shell">
        <section className="card auth-card">
          <h1>abun</h1>
          <p>Login with email OTP to access your cloud workspace.</p>
          <input aria-label="Email" placeholder="Email" value={auth.email} onChange={(e) => setAuth((p) => ({ ...p, email: e.target.value }))} />
          {auth.mode === 'OTP_SENT' && <input aria-label="OTP" placeholder="OTP code" value={auth.otp} onChange={(e) => setAuth((p) => ({ ...p, otp: e.target.value }))} />}
          {auth.mode === 'ANON' ? (
            <button disabled={working} onClick={() => void requestOtp()}>Send OTP</button>
          ) : (
            <button disabled={working} onClick={() => void verifyOtp()}>Verify OTP</button>
          )}
          {error && <p className="error">{error}</p>}
        </section>
      </main>
    );
  }

  const nowMs = Date.now();
  const activePomodoro = pomodoroSessions.find((s) => s.state === 'ACTIVE');
  const tasksWithStatus = tasks
    .map((task) => ({ ...task, status: (taskStatuses[task.id] ?? 'TODO') as TaskStatus }))
    .sort((a, b) => statusPriority(a.status) - statusPriority(b.status));
  const visibleRoutines = routines;
  const visibleAlarms = alarms;
  const currentTasks = tasksWithStatus.filter((t) => t.status === 'TODO' || t.status === 'IN_PROGRESS');
  const upcomingTasks = tasksWithStatus.filter((t) => t.status === 'COMPLETED' || t.status === 'CANCELLED');

  return (
    <main className="app-shell">
      <header className="topbar">
        <h1>abun</h1>
        <div className="topbar-actions">
          <button disabled={loading || working} onClick={() => void loadAll()}>{loading ? 'Syncing...' : 'Sync'}</button>
          <button disabled={working} onClick={logout}>Logout</button>
        </div>
      </header>

      <nav className="tabs" aria-label="Main tabs">
        <button className={mainTab === 'TODAY' ? 'active' : ''} onClick={() => setMainTab('TODAY')}>Today</button>
        <button className={mainTab === 'TASKS' ? 'active' : ''} onClick={() => setMainTab('TASKS')}>Tasks</button>
        <button className={mainTab === 'PREFERENCES' ? 'active' : ''} onClick={() => setMainTab('PREFERENCES')}>Preferences</button>
      </nav>

      {error && <p className="error">{error}</p>}
      {loading && <p className="muted">Refreshing data...</p>}

      {pendingDeletes.length > 0 && (
        <section className="card">
          <strong>Pending deletes</strong>
          {pendingDeletes.map((d) => (
            <div key={d.key} className="row">
              <span>{d.label} queued for deletion ({Math.max(1, Math.ceil((d.expiresAt - Date.now()) / 1000.0))}s)</span>
              <button onClick={() => undoDelete(d.key)}>Undo</button>
            </div>
          ))}
        </section>
      )}

      <section className="card pomodoro-strip">
        <strong>Pomodoro</strong>
        {activePomodoro ? <span>{activePomodoro.task_id ? tasks.find((t) => t.id === activePomodoro.task_id)?.title ?? activePomodoro.task_id : 'Solo focus'} {formatRemainingMs(new Date(activePomodoro.ends_at).getTime() - nowMs)}</span> : <span>No active timer</span>}
      </section>

      {mainTab === 'TODAY' && (
        <section className="grid">
          <article className="card">
            <h2>Today</h2>
            <label htmlFor="today-date">Date</label>
            <input id="today-date" type="date" value={selectedDate} onChange={(e) => setSelectedDate(e.target.value)} />
            <h3>Current</h3>
            {currentTasks.length === 0 ? <p className="empty">No current tasks.</p> : currentTasks.map((task) => <p key={task.id}>{task.title} · {task.status}</p>)}
            <h3>Upcoming</h3>
            {upcomingTasks.length === 0 ? <p className="empty">No upcoming tasks.</p> : upcomingTasks.map((task) => <p key={task.id}>{task.title} · {task.status}</p>)}
          </article>
          <article className="card">
            <h2>Journal</h2>
            {journalEntries.length === 0 ? <p className="empty">No journal entries for this date.</p> : journalEntries.map((entry, index) => (
              <p key={`${entry.eventTime}-${index}`}>{entry.taskTitle} · {entry.eventType} · {new Date(entry.eventTime).toLocaleTimeString()}{entry.content ? ` · ${entry.content}` : ''}</p>
            ))}
          </article>
        </section>
      )}

      {mainTab === 'TASKS' && (
        <section>
          <nav className="tabs secondary" aria-label="Task subtabs">
            <button className={taskTab === 'TASKS' ? 'active' : ''} onClick={() => setTaskTab('TASKS')}>Tasks</button>
            <button className={taskTab === 'ALARMS' ? 'active' : ''} onClick={() => setTaskTab('ALARMS')}>Alarms</button>
          </nav>

          {taskTab === 'TASKS' && (
            <section className="grid">
              <article className="card">
                <h2>Create task</h2>
                <input value={taskDraft} onChange={(e) => setTaskDraft(e.target.value)} placeholder="Task title" />
                <p className="helper">Task title uses current prefix setting.</p>
                <button disabled={working || !taskDraft.trim()} onClick={() => void runAction(async () => {
                  const title = `${preferences.titlePrefix} ${taskDraft}`.trim();
                  await client.createTask({ title });
                  setTaskDraft('');
                  await loadAll();
                }, 'Task created')}>Create</button>

                <h2>Create routine</h2>
                <input value={routineDraft.template_title} onChange={(e) => setRoutineDraft((p) => ({ ...p, template_title: e.target.value }))} placeholder="Routine title" />
                <input value={routineDraft.cron_schedule} onChange={(e) => setRoutineDraft((p) => ({ ...p, cron_schedule: e.target.value }))} placeholder="Cron" />
                <input value={routineDraft.timezone} onChange={(e) => setRoutineDraft((p) => ({ ...p, timezone: e.target.value }))} placeholder="Timezone" />
                <button disabled={working || !routineDraft.template_title || !routineDraft.cron_schedule || !routineDraft.timezone} onClick={() => void runAction(async () => {
                  await client.createRoutine(routineDraft);
                  setRoutineDraft((p) => ({ ...p, template_title: '' }));
                  await loadAll();
                }, 'Routine created')}>Create routine</button>
              </article>

              <article className="card">
                <h2>Tasks</h2>
                {tasksWithStatus.length === 0 ? <p className="empty">No tasks yet.</p> : tasksWithStatus.map((task) => (
                  <div key={task.id} className="row">
                    <span>{task.title} · {task.status}</span>
                    <div>
                      <button disabled={working} onClick={() => void runAction(() => appendEvent(task.id, 'PROGRESSED'), 'Task progressed')}>Progress</button>
                      <button disabled={working} onClick={() => void runAction(() => appendEvent(task.id, 'COMPLETED'), 'Task completed')}>Complete</button>
                      <button disabled={working} onClick={() => {
                        if (window.confirm('Delete this task?')) {
                          scheduleDelete('task', task.id, `Task "${task.title}"`);
                        }
                      }}>Delete</button>
                    </div>
                  </div>
                ))}

                <h2>Routines</h2>
                {visibleRoutines.length === 0 ? <p className="empty">No routines yet.</p> : visibleRoutines.map((routine) => (
                  <div key={routine.id} className="row">
                    <span>{routine.template_title} · {routine.cron_schedule} · {routine.timezone} · {routine.is_active ? 'Active' : 'Paused'}</span>
                    <div>
                      <button disabled={working} onClick={() => void runAction(async () => { await client.updateRoutine(routine.id, { is_active: !routine.is_active }); await loadAll(); }, 'Routine updated')}>{routine.is_active ? 'Pause' : 'Activate'}</button>
                      <button disabled={working} onClick={() => {
                        if (window.confirm('Delete this routine?')) {
                          scheduleDelete('routine', routine.id, `Routine "${routine.template_title}"`);
                        }
                      }}>Delete</button>
                    </div>
                  </div>
                ))}
              </article>
            </section>
          )}

          {taskTab === 'ALARMS' && (
            <section className="grid">
              <article className="card">
                <h2>Create alarm</h2>
                <select value={alarmDraft.task_id} onChange={(e) => setAlarmDraft((p) => ({ ...p, task_id: e.target.value }))}>
                  <option value="">Select task</option>
                  {tasks.map((t) => <option key={t.id} value={t.id}>{t.title}</option>)}
                </select>
                <input type="datetime-local" value={alarmDraft.trigger_time} onChange={(e) => setAlarmDraft((p) => ({ ...p, trigger_time: e.target.value }))} />
                <button disabled={working || !alarmDraft.task_id || !alarmDraft.trigger_time} onClick={() => void runAction(async () => {
                  await client.createAlarm({ task_id: alarmDraft.task_id, trigger_time: toIsoFromDateTimeLocal(alarmDraft.trigger_time), is_active: true });
                  setAlarmDraft((p) => ({ ...p, trigger_time: '' }));
                  await loadAll();
                }, 'Alarm created')}>Create alarm</button>
              </article>
              <article className="card">
                <h2>Alarms</h2>
                {visibleAlarms.length === 0 ? <p className="empty">No alarms yet.</p> : visibleAlarms.map((alarm) => (
                  <div key={alarm.id} className="row">
                    <span>{tasks.find((t) => t.id === alarm.task_id)?.title ?? alarm.task_id} · {new Date(alarm.trigger_time).toLocaleString()} · {alarm.is_active ? 'Active' : 'Paused'}</span>
                    <div>
                      <button disabled={working} onClick={() => void runAction(async () => { await client.updateAlarm(alarm.id, { is_active: !alarm.is_active }); await loadAll(); }, 'Alarm updated')}>{alarm.is_active ? 'Pause' : 'Activate'}</button>
                      <button disabled={working} onClick={() => {
                        if (window.confirm('Delete this alarm?')) {
                          scheduleDelete('alarm', alarm.id, `Alarm for ${tasks.find((t) => t.id === alarm.task_id)?.title ?? alarm.task_id}`);
                        }
                      }}>Delete</button>
                    </div>
                  </div>
                ))}
              </article>
            </section>
          )}
        </section>
      )}

      {mainTab === 'PREFERENCES' && (
        <section className="grid">
          <article className="card">
            <h2>Preferences</h2>
            <label>Title prefix</label>
            <input value={preferences.titlePrefix} onChange={(e) => setPreferences((p) => ({ ...p, titlePrefix: e.target.value }))} />
            <label>Default alarm lead minutes</label>
            <input type="number" min={1} value={preferences.defaultAlarmLeadMinutes} onChange={(e) => setPreferences((p) => ({ ...p, defaultAlarmLeadMinutes: Number(e.target.value) }))} />
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
              <option value="MONTH_DAY">MONTH_DAY</option>
              <option value="WEEKDAY_MONTH_DAY">WEEKDAY_MONTH_DAY</option>
            </select>
            <button disabled={working} onClick={() => void runAction(async () => {
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
            }, 'Preferences saved')}>Save preferences</button>
          </article>

          <article className="card">
            <h2>Pomodoro</h2>
            <label>Task</label>
            <select value={pomodoroDraft.taskId} onChange={(e) => setPomodoroDraft((p) => ({ ...p, taskId: e.target.value }))}>
              <option value="">Solo focus</option>
              {tasks.map((t) => <option key={t.id} value={t.id}>{t.title}</option>)}
            </select>
            <label>Phase</label>
            <select value={pomodoroDraft.phase} onChange={(e) => setPomodoroDraft((p) => ({ ...p, phase: e.target.value as PomodoroPhase }))}>
              <option value="FOCUS">FOCUS</option>
              <option value="SHORT_BREAK">SHORT_BREAK</option>
              <option value="LONG_BREAK">LONG_BREAK</option>
            </select>
            <button disabled={working || !!activePomodoro} onClick={() => void runAction(async () => {
              const now = new Date();
              const duration = pomodoroDraft.phase === 'FOCUS' ? preferences.focusMinutes : pomodoroDraft.phase === 'SHORT_BREAK' ? preferences.shortBreakMinutes : preferences.longBreakMinutes;
              const ends = new Date(now.getTime() + duration * 60_000);
              await client.createPomodoroSession({
                task_id: pomodoroDraft.taskId || null,
                phase: pomodoroDraft.phase,
                state: 'ACTIVE',
                started_at: now.toISOString(),
                ends_at: ends.toISOString(),
                duration_minutes: duration,
                task_update: 'NONE',
              });
              await loadAll();
            }, 'Timer started')}>Start timer</button>

            {activePomodoro && (
              <>
                <p className="helper">Active until {new Date(activePomodoro.ends_at).toLocaleTimeString()}</p>
                <label>Completion note</label>
                <input value={pomodoroDraft.note} onChange={(e) => setPomodoroDraft((p) => ({ ...p, note: e.target.value }))} />
                <label>Task update on complete</label>
                <select value={pomodoroDraft.taskUpdate} onChange={(e) => setPomodoroDraft((p) => ({ ...p, taskUpdate: e.target.value as PomodoroTaskUpdate }))}>
                  <option value="NONE">NONE</option>
                  <option value="PROGRESS">PROGRESS</option>
                  <option value="COMPLETE">COMPLETE</option>
                </select>
                <div className="row">
                  <button disabled={working} onClick={() => void runAction(() => finishPomodoro(activePomodoro.id, false), 'Timer completed')}>Complete timer</button>
                  <button disabled={working} onClick={() => void runAction(() => finishPomodoro(activePomodoro.id, true), 'Timer cancelled')}>Cancel timer</button>
                </div>
              </>
            )}

            <h3>Recent sessions</h3>
            {pomodoroSessions.length === 0 ? <p className="empty">No pomodoro sessions yet.</p> : pomodoroSessions.slice().sort((a, b) => (a.started_at < b.started_at ? 1 : -1)).slice(0, 8).map((s) => (
              <p key={s.id}>{s.phase} · {s.state} · {s.duration_minutes}m · {new Date(s.started_at).toLocaleString()}</p>
            ))}
          </article>
        </section>
      )}

      <section className="toast-stack" aria-live="polite" aria-atomic="true">
        {toasts.map((toast) => <p key={toast.id} className={`toast ${toast.type}`}>{toast.message}</p>)}
      </section>
    </main>
  );
}

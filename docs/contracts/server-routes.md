# Server Route Contract

This document freezes the current HTTP contract served by the Cloudflare Worker backend on `abun.tireless.dev`.

## Auth routes

- `POST /api/auth/otp/request`
  - Accepts an email payload.
  - Returns `204 No Content` after storing or refreshing the OTP.
- `POST /api/auth/otp/verify`
  - Accepts email and OTP.
  - Returns `200 OK` with an access token and user id.

Fixture source: `docs/contracts/server-fixtures/auth.json`

## Sync routes

- `GET /api/sync/preferences`
- `POST /api/sync/preferences`
- `GET /api/sync/routines`
- `POST /api/sync/routines`
- `GET /api/sync/tasks`
- `POST /api/sync/tasks`
- `GET /api/sync/alarms`
- `POST /api/sync/alarms`
- `GET /api/sync/task-events`
- `POST /api/sync/task-events`
- `GET /api/sync/pomodoro-sessions`
- `POST /api/sync/pomodoro-sessions`

The frozen contract test covers the task sync push route and asserts the returned `accepted_fields`.

Fixture source: `docs/contracts/server-fixtures/sync-tasks.json`

## Business API routes

- `GET /api/preferences`
- `GET /api/preferences/{key}`
- `PUT /api/preferences/{key}`
- `DELETE /api/preferences/{key}`
- `GET /api/routines`
- `GET /api/routines/{id}`
- `POST /api/routines`
- `PATCH /api/routines/{id}`
- `DELETE /api/routines/{id}`
- `GET /api/tasks`
- `GET /api/tasks/{id}`
- `POST /api/tasks`
- `PATCH /api/tasks/{id}`
- `DELETE /api/tasks/{id}`
- `GET /api/tasks/{id}/status`
- `GET /api/tasks/{id}/events`
- `POST /api/tasks/{id}/events`
- `GET /api/alarms`
- `GET /api/alarms/{id}`
- `POST /api/alarms`
- `PATCH /api/alarms/{id}`
- `DELETE /api/alarms/{id}`
- `GET /api/pomodoro-sessions`
- `GET /api/pomodoro-sessions/{id}`
- `POST /api/pomodoro-sessions`
- `PATCH /api/pomodoro-sessions/{id}`
- `DELETE /api/pomodoro-sessions/{id}`
- `GET /api/journals/{date}`

The frozen contract test seeds a task through `POST /api/tasks` and verifies `GET /api/tasks/fixture-task`.

Fixture source: `docs/contracts/server-fixtures/business-api.json`

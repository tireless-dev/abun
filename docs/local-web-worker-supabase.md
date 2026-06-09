# Local Web + Worker + Supabase

This project can be tested locally without Cloudflare-managed database bindings.

The local stack is:

- Supabase local for PostgreSQL
- Worker local via `wrangler dev`
- Vite local for the React web app

## Prerequisites

- `supabase` CLI installed
- `bun` installed
- `npm` installed

## Local env files

Create a local Worker env file from [`workers/api/.dev.vars.example`](../workers/api/.dev.vars.example):

```dotenv
DB_URL=postgresql://postgres:postgres@127.0.0.1:54322/postgres
ABUN_REQUIRE_AUTH=true
```

If you run the Worker on a port other than `8080`, create a local web env file from [`app/webApp/.env.local.example`](../app/webApp/.env.local.example):

```dotenv
VITE_ABUN_API_BASE_URL=http://127.0.0.1:8787
```

## Fast local loop

From the repo root:

1. Start and reset Supabase:

```bash
npm run dev:supabase
```

2. In a second terminal, run the Worker on `8080`:

```bash
npm run dev:api
```

3. In a third terminal, run Vite:

```bash
npm run dev:web
```

Open:

- `http://127.0.0.1:4173/app/`
- `http://127.0.0.1:8080/api/...`

Why `8080` matters:

- Vite proxies `/api` to `http://127.0.0.1:8080` in [`app/webApp/vite.config.ts`](../app/webApp/vite.config.ts)
- The Worker falls back to `DB_URL` when `HYPERDRIVE` is not present in [`workers/api/src/env.ts`](../workers/api/src/env.ts)

## Production-like local loop

To test the built web app as served by the Worker:

1. Build the web assets:

```bash
npm run dev:web:build
```

2. Run the Worker:

```bash
npm run dev:api
```

Open:

- `http://127.0.0.1:8080/app/`

The Worker serves `/app` from `app/webApp/dist` via [`workers/api/wrangler.jsonc`](../workers/api/wrangler.jsonc).

## Verification

Run the local checks from the repo root:

```bash
npm run local:verify
```

This runs:

- web tests
- Worker tests
- Worker typecheck

## Notes

- Local Supabase is the intended local database path. Do not rely on local Hyperdrive for this workflow.
- `workers/api/.dev.vars` is for local-only values and should stay uncommitted.
- `app/webApp/.env.local` is only needed when bypassing the default Vite proxy path.
- `dev:web` binds Vite to `127.0.0.1:4173` explicitly so local browser checks hit the same URL every time.

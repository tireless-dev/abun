# AGENTS Guidance

## Webapp Snapshot

Use this command to capture the webapp screenshot after the Vite dev server is running on `http://localhost:4173`:

```bash
npx playwright screenshot --device="Desktop Chrome" http://localhost:4173 /private/tmp/abun-webapp-fixed.png
```

Notes:
- Use `localhost` (not `127.0.0.1`) for this project screenshot workflow.
- Do not pass positional args like `vite 127.0.0.1 4173`; start Vite with proper flags instead (for example: `npm run start -- --host localhost --port 4173`).

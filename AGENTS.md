# AGENTS Guidance

## Core Principles

- **Ignore Web App**: For all development work, ignore the web app (`app/webApp`) unless explicitly requested.
- **Cross-Platform Alignment**: Ensure all changes align with the Kotlin Multiplatform (KMP) architecture and the server-side implementation.
- **Architecture & Product Design Alignment**: After every implementation, update the relevant architecture and product design documents so they match the implemented behavior.
- **Design System Alignment**: Treat the shared editorial Material 3 design system in `docs/base/shared-ui-design-system.md` and `app/sharedUI` as the source of truth for future UI work. New or modified shared UI should use the shared theme tokens and minimal editorial primitives rather than introducing feature-local styling, decorative effects, or one-off spacing/radius/color rules.
- **Pragmatic Test Coverage**: Add or update focused unit and component tests for behavior changes when they provide useful protection, but do not require strict TDD or failing-test-first flow for every task.
- **Target Support**: Ensure changes work for both Desktop and Android targets.
- **No Integration Tests**: Automated integration tests are not required for AI agents (they will be executed manually by the user). Focus on unit and component tests.

## Workflow & Verification

Use the Kotlin Multiplatform desktop app for routine UI and logic verification.

### Documentation Alignment

Every implementation must include documentation updates before it is considered complete:

- Update architecture docs when technical structure, persistence, sync, API, platform boundaries, or deployment/runtime behavior changes.
- Update product design docs when user-facing behavior, module semantics, workflows, states, or domain rules change.
- Keep base docs focused on cross-module architecture and module docs focused on domain/product meaning.
- If an implementation has no obvious architecture or product-design impact, still update the relevant status or implementation note so the docs explicitly reflect that the current behavior was reviewed.

### Run Desktop App
```bash
./gradlew :app:desktopApp:run
```

### Verify Changes (Unit/UI Tests)
```bash
# Verify shared UI components
./gradlew :app:sharedUI:jvmTest

# Verify desktop specific logic
./gradlew :app:desktopApp:test

# Verify core logic (common)
./gradlew :core:jvmTest
```

### Web App / Worker Follow-Through
```bash
# Rebuild the web assets served by the Worker at /app
npm run build -w webApp

# Verify Worker changes
npm run api:test
npm run api:typecheck
```

- `workers/api/wrangler.jsonc` serves `abun.tireless.dev/app` from `app/webApp/dist`.
- After any `app/webApp` change, run `npm run build -w webApp` before local Worker verification or deployment.
- After any `workers/api` change, run `npm run api:test` and `npm run api:typecheck`.
- If either `app/webApp` or `workers/api` changed and the task includes shipping to production, deploy from `workers/api` with `bun run deploy` after rebuilding the web assets.

## Notes
- Do not start the Vite dev server or capture web screenshots.
- Prefer the desktop app and shared Compose UI tests as the source of truth for UI validation.

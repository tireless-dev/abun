# AGENTS Guidance

## Core Principles

- **Ignore Web App**: For all development work, ignore the web app (`app/webApp`) unless explicitly requested.
- **Cross-Platform Alignment**: Ensure all changes align with the Kotlin Multiplatform (KMP) architecture and the server-side implementation.
- **Architecture & Product Design Alignment**: After every implementation, update the relevant architecture and product design documents so they match the implemented behavior.
- **TDD (Test-Driven Development)**: Follow TDD for every step of development. Write failing tests before implementing logic.
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
./gradlew :core:commonTest
```

## Notes
- Do not start the Vite dev server or capture web screenshots.
- Prefer the desktop app and shared Compose UI tests as the source of truth for UI validation.

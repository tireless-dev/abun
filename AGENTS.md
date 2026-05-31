# AGENTS Guidance

## Core Principles

- **Ignore Web App**: For all development work, ignore the web app (`app/webApp`) unless explicitly requested.
- **Cross-Platform Alignment**: Ensure all changes align with the Kotlin Multiplatform (KMP) architecture and the server-side implementation.
- **TDD (Test-Driven Development)**: Follow TDD for every step of development. Write failing tests before implementing logic.
- **Target Support**: Ensure changes work for both Desktop and Android targets.
- **No Integration Tests**: Automated integration tests are not required for AI agents (they will be executed manually by the user). Focus on unit and component tests.

## Workflow & Verification

Use the Kotlin Multiplatform desktop app for routine UI and logic verification.

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

# AGENTS Guidance

## Desktop App Workflow

For app UI work, ignore the web app unless the user explicitly asks for it. Build, run, and test the Kotlin Multiplatform desktop app instead.

Use this command to run the desktop app:

```bash
./gradlew :app:desktopApp:run
```

Use these commands to verify desktop/shared UI changes:

```bash
./gradlew :app:desktopApp:test
./gradlew :app:sharedUI:jvmTest
```

Notes:
- Do not start the Vite dev server or capture web screenshots for routine UI work.
- Prefer the desktop app and shared Compose UI tests as the source of truth.

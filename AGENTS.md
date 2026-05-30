# AGENTS Guidance

## Webapp Snapshot

Use this command to capture the webapp screenshot after the Vite dev server is running on `http://localhost:4173`:

```bash
npx playwright screenshot --device="Desktop Chrome" http://localhost:4173 /private/tmp/abun-webapp-fixed.png
```

Notes:
- Use `localhost` (not `127.0.0.1`) for this project screenshot workflow.
- Do not pass positional args like `vite 127.0.0.1 4173`; start Vite with proper flags instead (for example: `npm run start -- --host localhost --port 4173`).

## Android UI Snapshot

When making any Android UI change, verify it on the `resizable-api37` emulator before reporting completion:

```bash
emulator -avd resizable-api37 >/tmp/abun-android-emulator.log 2>&1 &
adb wait-for-device
./gradlew :app:androidApp:installDebug
adb shell am start -n dev.tireless.abun/.MainActivity
sleep 2
adb shell screencap -p /sdcard/abun-android-ui.png
adb pull /sdcard/abun-android-ui.png /private/tmp/abun-android-ui.png
```

Notes:
- If `resizable-api37` is already running, reuse it instead of starting another emulator.
- After capturing the screenshot, show `/private/tmp/abun-android-ui.png` to the user in the final response.
- If the build, install, launch, or screenshot step fails, report the failing command and the relevant error output.

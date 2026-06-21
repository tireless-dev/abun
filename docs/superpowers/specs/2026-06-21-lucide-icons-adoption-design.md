# Lucide Icons Adoption Design

## Purpose

Adopt Lucide through `compose-icons` as the shared icon source for Abun's Kotlin Multiplatform UI and begin using it in the current shared UI implementation.

## Current State

- `app/sharedUI` is the cross-platform UI source of truth for Desktop and Android.
- The shared UI already uses Compose Material 3 primitives and the editorial design tokens documented in `docs/base/shared-ui-design-system.md`.
- The current shared UI does not have existing `Icon(...)` callsites to migrate from Material icons.
- The most visible icon-like affordance in shared UI today is the `"+"` text used in the task creation floating action button.

## Decision

Adopt `compose-icons` with the Lucide pack directly in shared UI code without introducing a local icon abstraction layer.

This keeps the change small and explicit:

- add the Lucide dependency to `app/sharedUI`
- import Lucide icons directly in the shared UI files that render them
- replace the current textual add affordance with a Lucide add icon
- document Lucide as the shared icon family for future shared UI work

## Why This Approach

- it matches the requested direct-use approach rather than adding an app-local catalog layer
- it avoids a premature abstraction while the shared UI currently has a very small icon surface
- it preserves a single visual family going forward instead of mixing Material icons and Lucide icons
- it remains fully compatible with `commonMain`, keeping Desktop and Android aligned

## Implementation Shape

### Dependency

Add the Lucide artifact from `compose-icons` to the `commonMain` dependencies in `app/sharedUI/build.gradle.kts` and register the version in `gradle/libs.versions.toml`.

### Shared UI Usage

Update the shared UI to import and render Lucide icons directly from `commonMain`.

Initial in-scope usage:

- replace the `ExtendedFloatingActionButton` textual `"+"` icon in `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt` with a Lucide add icon

Out of scope for this change:

- creating a wrapper or icon catalog module
- migrating `app/webApp`
- broad decorative icon additions where no icon currently exists

### Styling Rules

- icons continue to inherit Material 3 content colors from the existing shared theme
- icon sizing should follow standard Material/Compose small action sizing unless the surrounding component defines it
- no feature-local icon colors, gradients, or decorative effects are introduced

## Testing

This change should follow TDD.

Planned first failing tests:

- a shared UI test that verifies the shared module exposes and can render the Lucide-backed add affordance in the main app UI

Validation commands:

- `./gradlew :app:sharedUI:jvmTest`
- `./gradlew :app:desktopApp:test`
- `./gradlew :core:jvmTest`
- `./gradlew :app:desktopApp:run`

## Documentation Updates

Update the following docs as part of the implementation:

- `docs/base/shared-ui-design-system.md`
  - state that Lucide is the shared icon family for app-owned icons in `app/sharedUI`
- `docs/base/technical-architecture.md`
  - note that `app/sharedUI` now uses Lucide via `compose-icons` for shared iconography while continuing to use Material 3 primitives for controls and app chrome
- `docs/base/information-architecture.md`
  - add a short note that shared presentation language includes a consistent Lucide icon family across shared UI surfaces

## Risks

- direct imports can spread third-party library references across UI files over time
- some Lucide icons may not visually match Material 3 spacing defaults without small layout adjustments

These risks are acceptable for the current icon surface. If icon usage grows substantially, a future refactor can introduce a shared icon catalog.

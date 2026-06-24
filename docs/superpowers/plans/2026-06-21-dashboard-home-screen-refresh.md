# Dashboard Home Screen Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the Day surface to Dashboard/Home, tighten its spacing, and simplify its header/content to match the requested denser home layout.

**Architecture:** Keep the route and tab model intact while renaming the shared surface component from `DayScreen` to `HomeScreen`. Scope the denser spacing to the dashboard surface by extending shared editorial primitives with optional compact padding so `Tasks` and `Settings` keep their existing rhythm.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Multiplatform, shared editorial UI primitives, JVM Compose UI tests, markdown docs.

---

### Task 1: Rename the shared Day surface to Home and update shell labels

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation/AppNavHost.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/ScreenLayoutTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/ScreenshotTestSupport.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppRouteMappingTest.kt`

- [ ] Rename `DayScreen` references to `HomeScreen` and update the top-level tab label from `Day` to `Dashboard`.
- [ ] Keep the existing `AppRoute.Day` route name unless a broader navigation rename is needed, so the change stays UI-facing.
- [ ] Update screenshot/test labels that still render `Day`.

### Task 2: Refresh the dashboard/home surface layout and spacing

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/DayScreen.kt` (rename to `HomeScreen.kt`)
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/Editorial.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/SurfaceSections.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/TaskRows.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`

- [ ] Collapse the summary header to just the selected date rendered as the main title.
- [ ] Remove the open/running/routines metric row from the home surface.
- [ ] Reduce dashboard-only surface padding, panel padding, and task-card padding through optional compact parameters on shared primitives.
- [ ] Keep timeline and pomodoro sections intact while shrinking their section-card spacing.

### Task 3: Align docs and verify the shared UI change

**Files:**
- Modify: `docs/tasks/functionality-design.md`
- Modify: `docs/tasks/technical-design.md`
- Test: `./gradlew :app:sharedUI:jvmTest`

- [ ] Update product/technical docs so they describe the `Dashboard` surface and its simplified denser layout.
- [ ] Run the shared UI JVM tests to verify the rename and layout changes.

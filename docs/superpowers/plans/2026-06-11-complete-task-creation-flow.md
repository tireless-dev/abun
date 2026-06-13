# Complete Task Creation Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the raw task-create sheet with a context-aware quick-create flow that uses human-friendly scheduling and backlog defaults aligned with the `Day` and `Tasks` surfaces.

**Architecture:** Add a shared quick-create draft/context model in shared UI, use it to drive `CreateTaskSheet`, and keep output normalized to the existing shared logic task-create contract. Wire the app to pass create context from the current tab, preserve the existing local write/ledger behavior, and document the final product and technical rules in the tasks docs.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform shared UI, shared logic controller/store, JVM desktop UI tests

---

### Task 1: Add quick-create draft and normalization rules

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskSaveDraftTest.kt`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run the targeted JVM test and confirm the missing quick-create behavior fails**
- [ ] **Step 3: Add creation-context defaults, schedule clearing behavior, and normalization helpers**
- [ ] **Step 4: Re-run the targeted JVM test and confirm it passes**

### Task 2: Replace the create sheet UI and pass creation context

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/ComponentScreenshotTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt`
- Create: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/CreateTaskSheetTest.kt`

- [ ] **Step 1: Write the failing UI tests for context defaults and schedule clearing**
- [ ] **Step 2: Run the targeted shared UI JVM tests and confirm they fail for the intended missing behavior**
- [ ] **Step 3: Update the sheet UI to title/detail/scheduling only, human-friendly controls, and context-aware defaults**
- [ ] **Step 4: Re-run the targeted shared UI JVM tests and confirm they pass**

### Task 3: Update task docs and run verification

**Files:**
- Modify: `docs/tasks/functionality-design.md`
- Modify: `docs/tasks/technical-design.md`

- [ ] **Step 1: Write the doc changes to reflect the implemented create flow and normalization rules**
- [ ] **Step 2: Run verification**

Run:

```bash
./gradlew :app:sharedUI:jvmTest
./gradlew :app:desktopApp:test
./gradlew :core:commonTest
```

Expected: touched tests pass, and any unrelated failures are called out explicitly.

- [ ] **Step 3: Perform a desktop app interaction check**

Run:

```bash
./gradlew :app:desktopApp:run
```

Expected: quick-create can be spot-checked manually from `Day` and `Tasks`.

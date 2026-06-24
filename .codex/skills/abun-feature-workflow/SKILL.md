---
name: abun-feature-workflow
description: Use when working on a new Abun feature or behavior change that should go through feature detailing, docs, planning, validation, and completion gates
---

# Abun Feature Workflow

Use this skill for Abun feature work. This is a self-product workflow, not a discovery workflow. Optimize for clarifying behavior, implementing it correctly in the KMP architecture, and keeping docs aligned with the code.

## Triggering

Use this skill when either condition is true:

- the user explicitly says `feature: ...`
- the request clearly describes a new Abun feature, behavior change, or module capability

Do not use this skill for:

- pure bug triage with no feature-shape change
- code review requests
- one-off factual questions
- `app/webApp` work unless the user explicitly asks for it

## Interaction Mode

Default to autopilot:

- keep moving when the next step is straightforward
- ask follow-up questions only when the feature is underspecified or a real fork has non-obvious consequences
- when questions are needed, ask one at a time

Focus questions on:

- intended behavior
- states and transitions
- edge cases
- affected modules and surfaces
- explicit non-goals

Do not ask whether users would like the feature. This workflow is for building Abun for its author first.

## Hard Gates

- no implementation before the feature is detailed enough
- no implementation before a written spec or doc update exists
- no completion claim without verification evidence
- no completion claim without documentation alignment
- no `app/webApp` detour unless explicitly requested

## Workflow

### 1. Feature detailing

Clarify the feature enough to implement it responsibly before planning or coding.

Cover:

- what problem it solves in the author's workflow
- expected user-facing behavior
- key states and state transitions
- edge cases and exclusions
- affected modules and surfaces
- explicit out-of-scope items

### 2. Spec alignment

Write or update the relevant docs before implementation.

Rules:

- put user-facing behavior in module product docs
- put cross-cutting technical behavior in base architecture docs
- if there is no durable design change, still add or update a note confirming docs were reviewed
- when the feature is substantial, write a dedicated spec under `docs/superpowers/specs/`

### 3. Implementation planning

Break the feature into small slices that are easy to implement and verify.

Each slice should identify:

- behavior being added
- shared/domain code to touch
- UI or platform wiring to touch
- tests or manual checks to run for the slice
- docs to update at completion

When a spec is approved and implementation is next, use the plan workflow and save the plan under `docs/superpowers/plans/`.

### 4. Implementation
Implement each slice with the lightest process that still leaves the change well-covered and easy to verify.

Typical flow:

1. identify the smallest meaningful implementation slice
2. add or update targeted automated tests when they provide useful protection
3. implement the minimal shared logic or UI behavior
4. run the relevant checks for that slice
5. refactor while keeping verification green

### 5. Validation

Use Abun's truthful validation path:

- run relevant unit and component tests
- use the desktop app for routine interaction checks
- ensure the implementation remains aligned for Desktop and Android targets

Preferred commands:

- `./gradlew :app:sharedUI:jvmTest`
- `./gradlew :app:desktopApp:test`
- `./gradlew :core:commonTest`
- `./gradlew :app:desktopApp:run`

Use additional module-specific tasks when the changed code requires them.

### 6. Completion and doc sync

Before calling the feature done:

- relevant tests must pass
- desktop validation must be performed
- affected architecture and product docs must reflect the implemented behavior
- unfinished design concepts must be marked `[TBI]`

## Abun Defaults

Follow `AGENTS.md` and keep the workflow aligned with the repo:

- ignore `app/webApp` unless explicitly requested
- prefer `app/sharedLogic`, `app/sharedUI`, and `core`
- preserve Kotlin Multiplatform alignment
- support Desktop and Android targets
- keep server-side alignment in mind for sync or API-facing changes
- prefer unit and component tests over integration tests
- use pragmatic verification depth based on the change size and risk

## Communication Style

When this skill is active:

- start by stating that you are using the Abun feature workflow
- keep updates short and momentum-oriented
- pause only for real tradeoffs, blockers, or underspecified behavior
- when work completes, summarize the implemented outcome, validation performed, and docs updated

# Abun Feature Workflow Skill Design

## Purpose

Define a repo-specific Codex skill that guides feature work for Abun from feature definition through planning, TDD implementation, validation, and documentation alignment.

This skill exists to support self-directed product development for Abun. It is not a user-discovery workflow and does not optimize for market validation. It optimizes for turning a personally desired feature into a well-specified, well-tested, cross-platform implementation with low process drift.

## Goals

- Turn feature requests into a consistent implementation workflow.
- Keep feature work aligned with Abun's Kotlin Multiplatform architecture.
- Enforce spec-first and TDD-first behavior before implementation.
- Use the desktop app as the primary interaction-validation surface.
- Require architecture and product-doc updates before a feature is considered complete.
- Keep momentum high by default, only pausing for meaningful forks.

## Non-Goals

- General-purpose product discovery or user research guidance.
- Web-app-first prototyping or validation.
- Integration-test automation requirements.
- A generic reusable workflow for unrelated repositories.

## Triggering Rules

The skill should trigger when either condition is true:

1. The user explicitly invokes the workflow with a phrase such as `feature: ...`.
2. The user's message clearly describes a new Abun feature, behavior change, or module capability that should go through spec, plan, and implementation.

The skill should not trigger for:

- pure bug triage without a new feature shape
- code review requests
- one-off factual questions
- explicit work on `app/webApp` unless the user asks for that target

## Default Interaction Mode

The skill uses autopilot by default.

Expected behavior:

- Move to the next workflow step automatically when the decision is straightforward.
- Ask targeted follow-up questions only when the feature definition is underspecified or a product/technical fork has non-obvious consequences.
- Prefer one question at a time when clarification is needed.
- Keep the conversation centered on behavior, states, edge cases, module fit, and implementation boundaries.

## Workflow

### 1. Feature detailing

Before any plan or code work, clarify the feature enough to implement it responsibly.

Focus areas:

- what problem this solves for the author's own workflow
- expected user-facing behavior
- key states and state transitions
- edge cases and exclusions
- affected modules and surfaces
- explicit out-of-scope items

This stage is not for validating whether other users would want the feature.

### 2. Spec alignment

Write or update the relevant design docs before implementation starts.

Documentation rules:

- put user-facing behavior and semantics in module product docs
- put cross-cutting technical rules in base architecture docs
- if the feature changes no enduring design rule, add a status note confirming the docs were reviewed

The workflow may create a dedicated spec document when the feature is substantial or spans multiple changes.

### 3. Implementation planning

Break the feature into implementation slices that are small enough for TDD and easy verification.

Each slice should identify:

- the behavior being added
- the shared/domain code to touch
- the UI or platform wiring to touch
- the tests that should fail first
- the docs affected at completion

### 4. TDD implementation

Each implementation slice follows a strict red-green-refactor loop:

1. write or update a failing test
2. implement the minimal shared logic or UI behavior
3. refactor while preserving the passing state

Test emphasis:

- common/unit tests
- shared UI/component tests
- desktop-target tests where needed

Do not rely on integration tests as a required gate.

### 5. Validation

Use the truthful repo validation path:

- run relevant Gradle tests
- use the desktop app for routine interaction checks
- ensure the implementation is structurally correct for Desktop and Android targets

Preferred validation commands:

- `./gradlew :app:sharedUI:jvmTest`
- `./gradlew :app:desktopApp:test`
- `./gradlew :core:commonTest`
- `./gradlew :app:desktopApp:run`

Additional tasks may be used when the touched code requires them.

### 6. Completion and doc sync

Before a feature is considered done:

- tests for the touched behavior must pass
- desktop validation must be performed
- relevant architecture or product docs must match the implemented behavior
- unfinished concepts must be marked `[TBI]` rather than implied complete

## Hard Gates

The skill must enforce these rules:

- no implementation before the feature is detailed enough
- no implementation before a written spec or doc update exists
- no implementation slice without a failing test first
- no completion claim without verification evidence
- no completion claim without documentation alignment
- no `app/webApp` detour unless explicitly requested

## Repo-Specific Guidance

The skill should bias toward these paths and assumptions:

- prefer `app/sharedLogic`, `app/sharedUI`, and `core`
- preserve Kotlin Multiplatform alignment
- support Desktop and Android targets
- treat the desktop app as the fastest reliable interaction-validation surface
- keep server-side alignment in mind when feature behavior crosses sync/API boundaries

## Suggested Skill Shape

Create a repo-local skill named `abun-feature-workflow`.

Recommended contents:

- `SKILL.md` with triggering rules, workflow, hard gates, and Abun-specific validation expectations
- optional `agents/openai.yaml` metadata

No extra README or process documents should be created inside the skill directory.

## Open Questions Resolved

- Discovery mode is intentionally excluded.
- Autopilot is the default interaction style.
- The workflow supports both auto-detected feature requests and explicit trigger phrases.
- The skill should live in the Abun repo and be tightly coupled to Abun conventions.

## Success Criteria

The skill is successful if, when the user introduces a new feature, Codex reliably:

1. details the feature instead of jumping to code
2. updates or writes the right docs before implementation
3. creates an implementation plan with test-first slices
4. implements via TDD in the KMP/shared-first architecture
5. validates through tests and desktop checks
6. updates docs before declaring the work complete

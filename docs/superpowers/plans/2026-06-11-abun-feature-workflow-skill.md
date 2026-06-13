# Abun Feature Workflow Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a repo-local Codex skill that guides Abun feature work through feature detailing, spec/doc alignment, planning, TDD implementation, desktop validation, and completion gates.

**Architecture:** Create a repo-local skill under `.codex/skills/abun-feature-workflow` so the workflow lives with the codebase. Keep the skill concise and repo-aware, with a single `SKILL.md` that encodes triggering rules, interactive autopilot behavior, hard gates, and Abun-specific validation expectations, plus optional UI metadata in `agents/openai.yaml`.

**Tech Stack:** Markdown skill files, Codex skill metadata, repo docs under `docs/superpowers/`

---

### Task 1: Create the repo-local skill scaffold

**Files:**
- Create: `.codex/skills/abun-feature-workflow/SKILL.md`
- Create: `.codex/skills/abun-feature-workflow/agents/openai.yaml`

- [ ] **Step 1: Write the failing test**

Use a filesystem expectation as the red step: the skill directory and required files should not exist yet.

```bash
test ! -f .codex/skills/abun-feature-workflow/SKILL.md
test ! -f .codex/skills/abun-feature-workflow/agents/openai.yaml
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
ls .codex/skills/abun-feature-workflow
```

Expected: `ls` fails because the directory does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create the two files with the minimal required structure:

```markdown
---
name: abun-feature-workflow
description: Use when working on a new Abun feature or behavior change that should go through feature detailing, docs, planning, TDD, validation, and completion gates
---
```

```yaml
display_name: Abun Feature Workflow
short_description: Guides Abun feature work from detail to verification.
default_prompt: Help me define and implement an Abun feature with the repo workflow.
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
test -f .codex/skills/abun-feature-workflow/SKILL.md
test -f .codex/skills/abun-feature-workflow/agents/openai.yaml
```

Expected: both commands succeed with no output.

- [ ] **Step 5: Commit**

```bash
git add .codex/skills/abun-feature-workflow/SKILL.md .codex/skills/abun-feature-workflow/agents/openai.yaml
git commit -m "feat: scaffold abun feature workflow skill"
```

### Task 2: Encode the workflow and hard gates in the skill

**Files:**
- Modify: `.codex/skills/abun-feature-workflow/SKILL.md`
- Test: `.codex/skills/abun-feature-workflow/SKILL.md`

- [ ] **Step 1: Write the failing test**

Define the required content checks before writing the full skill:

```bash
rg -n "feature detailing|spec alignment|implementation planning|TDD implementation|validation|completion" .codex/skills/abun-feature-workflow/SKILL.md
rg -n "no implementation before|no completion claim without|app/webApp" .codex/skills/abun-feature-workflow/SKILL.md
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
rg -n "feature detailing|spec alignment|implementation planning|TDD implementation|validation|completion" .codex/skills/abun-feature-workflow/SKILL.md
```

Expected: no matches or incomplete matches because the scaffold is still minimal.

- [ ] **Step 3: Write minimal implementation**

Expand `SKILL.md` to include:

```markdown
# Abun Feature Workflow

## When to Use

- new Abun feature requests
- behavior changes that need docs, plan, and implementation
- explicit `feature:` requests

## Workflow

1. Feature detailing
2. Spec alignment
3. Implementation planning
4. TDD implementation
5. Validation
6. Completion and doc sync

## Hard Gates

- no implementation before the feature is detailed enough
- no implementation before a written spec or doc update exists
- no implementation slice without a failing test first
- no completion claim without verification evidence
- no completion claim without documentation alignment
- no `app/webApp` detour unless explicitly requested
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
rg -n "Feature detailing|Spec alignment|Implementation planning|TDD implementation|Validation|Completion and doc sync" .codex/skills/abun-feature-workflow/SKILL.md
rg -n "no implementation before the feature is detailed enough|no completion claim without verification evidence|app/webApp" .codex/skills/abun-feature-workflow/SKILL.md
```

Expected: both commands return matching lines from the skill.

- [ ] **Step 5: Commit**

```bash
git add .codex/skills/abun-feature-workflow/SKILL.md
git commit -m "feat: define abun feature workflow skill"
```

### Task 3: Add Abun-specific workflow guidance and metadata polish

**Files:**
- Modify: `.codex/skills/abun-feature-workflow/SKILL.md`
- Modify: `.codex/skills/abun-feature-workflow/agents/openai.yaml`
- Test: `.codex/skills/abun-feature-workflow/SKILL.md`

- [ ] **Step 1: Write the failing test**

Define the repo-specific expectations the final skill must mention:

```bash
rg -n "app/sharedLogic|app/sharedUI|core|desktop app|Desktop and Android|AGENTS.md|docs" .codex/skills/abun-feature-workflow/SKILL.md
rg -n "Abun Feature Workflow|feature work" .codex/skills/abun-feature-workflow/agents/openai.yaml
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
rg -n "app/sharedLogic|app/sharedUI|core|desktop app|Desktop and Android|AGENTS.md|docs" .codex/skills/abun-feature-workflow/SKILL.md
```

Expected: missing matches for one or more repo-specific guidance items.

- [ ] **Step 3: Write minimal implementation**

Add concise repo-specific guidance to `SKILL.md` and refine metadata:

```markdown
## Abun Defaults

- follow `AGENTS.md`
- prefer `app/sharedLogic`, `app/sharedUI`, and `core`
- use the desktop app for routine validation
- keep Desktop and Android support aligned
- update relevant architecture and product docs before completion
```

```yaml
display_name: Abun Feature Workflow
short_description: Repo-specific feature workflow for Abun.
default_prompt: Guide this Abun feature from detail and docs through TDD, validation, and completion.
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
rg -n "app/sharedLogic|app/sharedUI|core|desktop app|Desktop and Android|AGENTS.md|docs" .codex/skills/abun-feature-workflow/SKILL.md
rg -n "Abun Feature Workflow|Repo-specific feature workflow for Abun|Guide this Abun feature" .codex/skills/abun-feature-workflow/agents/openai.yaml
```

Expected: both commands return matches for the final guidance and metadata.

- [ ] **Step 5: Commit**

```bash
git add .codex/skills/abun-feature-workflow/SKILL.md .codex/skills/abun-feature-workflow/agents/openai.yaml
git commit -m "feat: finalize abun feature workflow skill"
```

### Task 4: Verify the skill and sync supporting docs

**Files:**
- Modify: `docs/superpowers/specs/2026-06-11-abun-feature-workflow-skill-design.md`
- Test: `.codex/skills/abun-feature-workflow/SKILL.md`
- Test: `.codex/skills/abun-feature-workflow/agents/openai.yaml`

- [ ] **Step 1: Write the failing test**

Define the final verification commands:

```bash
test -s .codex/skills/abun-feature-workflow/SKILL.md
test -s .codex/skills/abun-feature-workflow/agents/openai.yaml
rg -n "repo-local skill named `abun-feature-workflow`" docs/superpowers/specs/2026-06-11-abun-feature-workflow-skill-design.md
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
test -s .codex/skills/abun-feature-workflow/SKILL.md
test -s .codex/skills/abun-feature-workflow/agents/openai.yaml
```

Expected: before the previous tasks are done, one or both checks fail.

- [ ] **Step 3: Write minimal implementation**

Ensure the spec remains aligned with the final skill shape and content, updating wording only if needed to match the implemented result.

```markdown
## Suggested Skill Shape

Create a repo-local skill named `abun-feature-workflow`.
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
test -s .codex/skills/abun-feature-workflow/SKILL.md
test -s .codex/skills/abun-feature-workflow/agents/openai.yaml
rg -n "repo-local skill named `abun-feature-workflow`" docs/superpowers/specs/2026-06-11-abun-feature-workflow-skill-design.md
```

Expected: all commands succeed.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-06-11-abun-feature-workflow-skill-design.md .codex/skills/abun-feature-workflow/SKILL.md .codex/skills/abun-feature-workflow/agents/openai.yaml
git commit -m "docs: align abun workflow skill spec"
```

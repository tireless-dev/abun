# Base Information Architecture

## Purpose

This document defines the shared product structure that all Abun modules must follow. It does not define module-specific behavior such as task history semantics or finance workflows.

## Product Shape

Abun is a local-first personal system organized as a set of configurable modules over a shared platform. Each module owns its own domain rules while reusing the same identity, persistence, sync, and ownership model.

The shared presentation language across modules uses the **Swiss Precision & Restraint (B1)** design system, with consistent Lucide iconography, warm stone canvas, flat outlined surfaces, and unified bottom-anchored command navigation.

Current module status:

- `tasks`: active module (`Tasks`, `Routines`, `Pomodoro`)
- `finance`: active module (double-entry core, clearing groups, statements, installments, pacing budget, runway cockpit, and local-first sync)
- `notes`: upcoming module (knowledge & quick captures)
- `things`: upcoming module (inventory, warranties, and physical assets)

## Navigation Architecture

### Floating Command Pill Navigation

The application eliminates legacy bottom navigation tabs and floating action buttons in favor of a centered, bottom-floating **Floating Command Pill** (`FloatingCommandPill`):

1. **Workspace Switcher (Left)**:
   - Reflects the active tab.
   - Shows colored status dots strictly for enabled `AppModule`s in order (`Tasks`: Indigo, `Finance`: Emerald, `Notes`: Amber, `Things`: Purple).
   - Toggles the expanded `FloatingCommandSheet`.
2. **Contextual Action Pill (Right)**:
   - High-contrast white button bound to the primary action of the current workspace:
     - `Today`: `+ Capture`
     - `Tasks`: `+ 新建任务` (or `+ 新建例程` / `+ 开始专注` on subtabs)
     - `Finance`: `+ 记一笔`
     - `Settings`: `← 返回主页`
3. **Floating Command Sheet (`FloatingCommandSheet`)**:
   - Expands upward over a dimmed backdrop when tapping the switcher.
   - Surfaces **Quick Capture** pills for active modules.
   - Renders a 2-column bento grid for enabled workspaces with live summary metrics.
   - Provides a direct link to `Settings & Preferences`.

### Top-Bar Inline 3-Segment Sub-Navigation

Modules with multiple related sub-surfaces use inline 3-segment pill controls directly inside the `TopAppBar`:

- **Tasks Module**: `任务` (Tasks) | `例程` (Routines) | `番茄` (Pomodoro) via `TaskTopBarSubtabSelector`.
- **Finance Module**: `总览` (Cockpit) | `明细` (Ledger) | `清算` (Clearing Hub) via `TopBarSegmentedSubnav`.
- **Dashboard / Today**: Date title and `● LOCAL-FIRST` status indicator.
- **Settings**: Direct header with `LOCAL-FIRST · SYNCED` badge.

## Shared Concepts

### Configurable Modules (`AppModule`)

User workspaces are modular and user-configurable:
- Available modules: `AppModule.TASKS`, `AppModule.FINANCE`, `AppModule.NOTES`, `AppModule.THINGS`.
- Modules can be enabled or disabled in `Settings`.
- Disabling a module removes it from the command sheet bento grid and pill indicator dots, keeping the interface uncluttered for focused workflows.

### Module Structure

A module is a product area with its own user-facing functionality and its own design documents. Every real module should define:

- `model-design.md`
- `functionality-design.md`
- `technical-design.md`

If a module grows into distinct areas, those areas may become subfolders with the same structure.

### Record Identity

Each domain record has a stable identifier that survives local edits, sync cycles, and device boundaries. Modules may use random IDs for user-created records and deterministic IDs for system-generated records when idempotency matters.

### Ownership

All synced records are owned by an authenticated user on the server-side API. The client may operate offline, but remote persistence and sync acceptance are always scoped by user ownership.

### Local-first Behavior

The app writes locally first. Remote persistence is asynchronous and must not be required for ordinary interaction. Module designs should assume:

- local reads are authoritative for current UI rendering
- local writes happen before remote sync
- remote changes arrive through pull and merge, not by directly mutating UI state from network events
- guest-mode onboarding dismissal is a per-device local behavior and not a synced cross-device preference
- theme preference is a per-device presentation choice and not a synced cross-device preference
- auth session persistence is a per-device runtime state and not synced product data
- revoking or expiring a device session should not delete local records; it only removes remote authorization until the user signs in again
- when a device session expires, the shared auth flow returns the app to guest mode with a readable re-login message rather than silently discarding the session state

### Status Tagging

All docs may mark unfinished concepts with `[TBI]`. This tag means the concept belongs in the intended design but should not be read as implemented.

## Document Ownership

- `base/` owns cross-module structure and shared technical rules
- module folders own domain semantics and feature behavior
- the global backlog owns execution order until work is moved into Git issues

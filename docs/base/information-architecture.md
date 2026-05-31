# Base Information Architecture

## Purpose

This document defines the shared product structure that all Abun modules must follow. It does not define module-specific behavior such as task history semantics or finance workflows.

## Product Shape

Abun is a local-first personal system organized as a set of modules over a shared platform. Each module owns its own domain rules while reusing the same identity, persistence, sync, and ownership model.

Current module status:

- `tasks`: active module
- `finance`: placeholder
- `notes`: placeholder

## Shared Concepts

### Module

A module is a product area with its own user-facing functionality and its own design documents. Every real module should define:

- `model-design.md`
- `functionality-design.md`
- `technical-design.md`

If a module grows into distinct areas, those areas may become subfolders with the same structure.

### Record identity

Each domain record has a stable identifier that survives local edits, sync cycles, and device boundaries. Modules may use random IDs for user-created records and deterministic IDs for system-generated records when idempotency matters.

### Ownership

All synced records are owned by an authenticated user on the server. The client may operate offline, but server persistence and sync acceptance are always scoped by user ownership.

### Local-first behavior

The app writes locally first. Remote persistence is asynchronous and must not be required for ordinary interaction. Module designs should assume:

- local reads are authoritative for current UI rendering
- local writes happen before remote sync
- remote changes arrive through pull and merge, not by directly mutating UI state from network events

### Status tagging

All docs may mark unfinished concepts with `[TBI]`. This tag means the concept belongs in the intended design but should not be read as implemented.

## Document Ownership

- `base/` owns cross-module structure and shared technical rules
- module folders own domain semantics and feature behavior
- the global backlog owns execution order until work is moved into Git issues

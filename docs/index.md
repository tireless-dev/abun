# Abun Product Documentation

This documentation set is the working product and architecture reference for Abun. It separates shared platform decisions from module-specific design so product decisions, implementation work, and agent execution can all refer to the same structure.

## Current Module Map

- [Base architecture](./base/information-architecture.md)
- [Tasks module](./tasks/model-design.md)
- [Finance placeholder](./finance/README.md)
- [Notes placeholder](./notes/README.md)
- [Prioritized backlog](./backlog.md)

## Reading Order

1. [Base information architecture](./base/information-architecture.md)
2. [Base technical architecture](./base/technical-architecture.md)
3. [Base sync architecture](./base/sync-architecture.md)
4. [Tasks model design](./tasks/model-design.md)
5. [Tasks functionality design](./tasks/functionality-design.md)
6. [Tasks technical design](./tasks/technical-design.md)
7. [Prioritized backlog](./backlog.md)

## Status Legend

- `Implemented`: present in the current codebase and intended to remain.
- `Partial`: present but still needs refinement or completion.
- `[TBI]`: intended product or technical concept that is not fully implemented yet.
- `Placeholder`: reserved module or document with no committed design yet.

Use `[TBI]` consistently across all module docs when a concept belongs in the design but should not be treated as done.

## Roadmap Summary

- Stabilize the shared architecture docs so future modules inherit the same local-first, identity, and ownership rules.
- Finish tightening the tasks module around the ledger-based task model, routines, alarms, and pomodoro-related behavior.
- Convert the current backlog into Git issues once the rewritten docs are reviewed.
- Fill in `finance` and `notes` after the base and tasks documentation structure proves workable.

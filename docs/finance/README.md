# Finance Module Documentation

Status: `Implemented` (Phases 1–4 fully implemented and verified across KMP, Cloudflare Workers, and Supabase)

The Finance module is a local-first double-entry personal finance and cashflow safety system. It combines Beancount-grade double-entry rigor with split-level narrations, zero-sum clearing groups (`ClearingGroup`), automated credit card statement & installment schedules, guardrail pacing budgets, a 90-day Cashflow Runway projector, loan prepayment optimization, and multi-device HLC synchronization.

## Design Documents

1. [Finance model design](./model-design.md) — Core entities (`Account`, `Transaction`, `Split`, `ClearingGroup`, `AmortizationPlan`, `BudgetRule`) and mathematical invariants (`SafeToSpend`, `CashflowRunway`).
2. [Finance functionality design](./functionality-design.md) — User-facing surfaces, automatic statement grouping, combined repayment flows, installment wizard, pass-through accounts, and `Auto-Pad` reconciliation.
3. [Finance technical design](./technical-design.md) — SQLite/PostgreSQL schema, minor-unit arithmetic, deterministic IDs, local write invariants, and HLC sync rules.
4. [Finance implementation plan](./implementation-plan.md) — Phased vertical-slice roadmap and verification strategy.

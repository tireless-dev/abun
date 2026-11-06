# Finance Module Implementation Plan

## Goal

Deliver the Abun Finance module in iterative, verifiable vertical slices—starting from the local-first double-entry & clearing-group core, then layering credit card statements & installment batch generation, and culminating in the Pacing Budget & Cashflow Runway cockpit.

## Architecture & Verification Strategy

- **Primary Development & Verification Target**: KMP (`core`, `app/sharedLogic`, `app/sharedUI`) validated via `./gradlew :core:jvmTest :app:sharedLogic:jvmTest :app:sharedUI:jvmTest` and interactive desktop checks via `./gradlew :app:desktopApp:run`.
- **Server & Sync Alignment**: Supabase PostgreSQL migrations (`supabase/migrations/`) and Cloudflare Worker sync endpoints (`workers/api`) verified via `npm run api:test && npm run api:typecheck`.

---

## Phase 1: Double-Entry Ledger Core & Multi-Group Clearing Engine (MVP Foundation)

**Objective**: Establish the local SQLite schema, domain models, zero-sum transaction validation, split-level narration, pass-through accounts, and `ClearingGroup` many-to-many balancing engine.

### Slice 1.1: Domain Models, Multi-Currency Weights & Pure Validation Logic (`app/sharedLogic` & `core`)
- [x] Define `FinanceAccount`, `FinanceTransaction`, `FinanceSplit`, `ClearingGroup`, `SplitClearingGroup`, and `ExchangeRate` in `core/src/commonMain/kotlin/dev/tireless/abun/finance/FinanceModels.kt`.
- [x] Implement exact minor-unit monetary arithmetic (`FinanceCurrencies` with currency exponent scale: `2` for `CNY`/`USD`/`HKD`, `0` for `JPY`) and Beancount-style cross-currency balancing weights (`weight_amount_minor`, `weight_currency` corresponding to `@@` total cost / `@` rate) in `core/src/commonMain/kotlin/dev/tireless/abun/finance/FinanceValidators.kt`:
  - `Draft`: allows $0$ or $1$ split, or unbalanced splits.
  - `Cleared` / `Scheduled`: requires $\ge 2$ active splits, `sign(amount_minor) == sign(weight_amount_minor)`, and $\sum \text{weight\_amount\_minor} = 0$ per `weight_currency`.
- [x] Implement `ClearingGroupEvaluator`:
  - Computes `realizedBalance` and `projectedBalance` in the group's `anchor_currency` (supporting cross-currency settlement where a split matches via `currency` or `weight_currency`), transitioning group status between `Active`, `Settled`, and `Overdrawn`.
- [x] Create matching TypeScript contract mirrors in `workers/api/src/services/finance-types.ts` and `app/webApp/src/api/finance-types.ts`.
- [x] **Verification**: Unit tests in `:core:jvmTest` (`FinanceModelTest.kt`) and Worker Vitest (`finance-contracts.spec.ts`) covering single- and cross-currency transactions (`USD @@ CNY`), split narrations, and multi-group intersection math. All tests passing.

### Slice 1.2: Local SQLDelight Persistence (`AbunDatabase.sq` & `LocalStore.kt`)
- [x] Add tables `finance_account`, `finance_exchange_rate`, `finance_transaction`, `finance_split`, `finance_clearing_group`, and `finance_split_clearing_group` to `AbunDatabase.sq`.
- [x] Seed default starter account hierarchy (`Assets:Bank`, `Liabilities:CreditCard`, `Liabilities:Loan`, `Expenses:Food`, `Expenses:Finance:Interest`, `Expenses:Adjustment:Unallocated`, `Income:Salary`).
- [x] Implement atomic write operations in `LocalStore.kt`:
  - Create/edit/delete `FinanceAccount` (with `currency`, `include_in_net_worth`, and `is_pass_through` flags).
  - Create/edit/delete `FinanceTransaction` + `FinanceSplit`s + `SplitClearingGroup` bindings in a single SQLite transaction (and auto-record an `ImplicitFromSplit` row in `finance_exchange_rate` when a cross-currency `@@` split is committed).
  - Implement `Auto-Pad` balance assertion helper (computes delta between recorded and actual balance and writes an adjustment transaction).
- [x] **Verification**: SQLite store tests in `:app:sharedLogic:jvmTest`.

### Slice 1.3: Ledger & Account Management UI (`app/sharedUI`)
- [x] Add `Finance` top-level navigation destination in `AppRoutes.kt` and `AppNavHost.kt`.
- [x] Build `FinanceLedgerScreen`:
  - Standard card view and expandable **Beancount Ledger view** (displaying every split's account, signed amount, `@@` cross-currency weight when present, split-level `narration`, and `ClearingGroup` pills).
  - Filter bar: `All`, `Drafts (Inbox)`, `Scheduled`, `By Account`, `By Clearing Group`.
- [x] Build `CreateTransactionSheet`:
  - Fast 2-split mode (`Expense`, `Income`, `Transfer/Repay`) with automatic cross-currency conversion prompt (`@@` total cost / `@` rate) when accounts have different currencies + expandable **Multi-Split editor** with auto-balance fill for the last blank split.
  - Support saving incomplete entries as `Draft`.
- [x] Build `AccountsSheet` / screen with `Auto-Pad` balance reconciliation dialog and `ExchangeRate` management.
- [x] **Verification**: Compose UI tests in `:app:sharedUI:jvmTest` (`FinanceUiTest.kt`) and desktop verification (`./gradlew :app:sharedUI:jvmTest`). All 92 UI tests passing.

---

## Phase 2: Credit Card Statements, Combined Repayments & Installment Generator

**Objective**: Eliminate manual grouping friction via automatic credit card billing cycle inference, batch installment generation, and one-click combined repayment workflows.

### Slice 2.1: Automatic Credit Card Statement Cycle Engine
- [x] Implement deterministic billing cycle calculator (`cg-stmt:${accountId}:${yyyy}-${mm}`) based on `Account.statement_day` and `Account.due_day`.
- [x] Auto-bind credit card expense and refund splits to their active statement `ClearingGroup` upon local write.
- [x] Support one-tap statement cycle reassignment (for late-posted end-of-month transactions).
- [x] **Verification**: Unit tests verifying cycle boundaries, refunds reducing statement balance, and full/partial statement repayment clearing (`StatementCycleTest.kt`, `statement-cycle.spec.ts`, and `FinanceStoreTest.kt`). All tests passing.

### Slice 2.2: Loan & Installment Batch Generator (`AmortizationPlan`)
- [x] Add `finance_amortization_plan` table to `AbunDatabase.sq`.
- [x] Implement `AmortizationEngine` supporting:
  - `EqualInstallment` (等额本息)
  - `EqualPrincipal` (等额本金)
  - `FlatFee` (分期固定手续费)
  - `InterestFirst` (先息后本)
- [x] Batch-generate deterministic `Scheduled` transactions (`tx-plan:${planId}:p${i}`), binding principal splits to both the `LoanLifecycle` group and the target month's `CreditStatement` group (if applicable), while routing interest/fees to `Expenses:Finance:Interest`.
- [x] TypeScript mirror in `workers/api/src/services/amortization.ts` and `app/webApp/src/api/amortization.ts`.
- [x] **Verification**: Unit tests verifying that generated principal splits across all $N$ periods sum with initial borrowing to `0` (`AmortizationEngineTest.kt` and `amortization.spec.ts`). All tests passing.

### Slice 2.3: Clearing & Debt Hub UI + Combined Repayment Sheet
- [x] Build `ClearingHubScreen` displaying:
  - Monthly credit card statement cards with **`[One-Click Repay]`**.
  - Active loan & installment progress cards (`Paid X/N periods`, remaining principal bar).
  - Active `InstantBatch` and `Reimbursement` groups (highlighting any non-zero discrepancy).
- [x] Build `CombinedRepaymentSheet`:
  - Select multiple due statements / loan tranches $\rightarrow$ auto-populate liability repayment splits + create an `InstantBatch` group $\rightarrow$ add one or multiple bank funding splits with custom split narrations.
- [x] Build `AmortizationWizardSheet` for creating loans or converting a credit card transaction/statement into installments.
- [x] **Verification**: Compose UI tests in `:app:sharedUI:jvmTest` (`FinanceUiTest.kt`). All tests passing.

---

## Phase 3: Guardrail Pacing Budget & Cashflow Runway Cockpit (Surpassing YNAB)

**Objective**: Deliver the forward-looking decision engine that answers *"How much can I safely spend today?"* and *"When might I run out of money?"*

### Slice 3.1: Guardrail Pacing Budget Engine (`BudgetRule`)
- [x] Add `finance_budget_rule` table to `AbunDatabase.sq`.
- [x] Implement `BudgetEvaluator`:
  - Computes monthly spent amount across `GlobalDiscretionary` or specific `Account` subtrees (normalizing foreign-currency splits into `BudgetRule.currency` via `weight_amount_minor` or `ExchangeRate`, including transfers into `is_pass_through = true` accounts, and excluding fixed debt/interest accounts).
  - Computes `Time Elapsed Ratio` vs. `Spend Ratio` and `Pacing Delta`.
- [x] TypeScript mirror in `workers/api/src/services/budget-evaluator.ts` and `app/webApp/src/api/budget-evaluator.ts`.
- [x] **Verification**: Unit tests for subtree matching, multi-currency expense normalization, pass-through account inclusion, and pacing calculations (`BudgetEvaluatorTest.kt` and `budget-evaluator.spec.ts`). All tests passing.

### Slice 3.2: Safe-to-Spend & 90-Day Cashflow Runway Projector
- [x] Implement `CashflowRunwayEngine`:
  - Computes `SafeToSpend` = `LiquidAssets` - `UpcomingCommittedOutflows` - `RemainingLivingBudget`.
  - Projects daily liquid cash balance over the next 30 / 90 days by combining:
    1. `Scheduled` transactions (loan installments, recurring income/bills)
    2. Unsettled `CreditStatement` / `ClearingGroup` obligations by `due_date`
    3. Expected daily discretionary burn rate derived from `BudgetRule`
  - Identifies `DeficitDate` (first date where projected cash $< 0$) and `MinimumCashTrough`.
- [x] TypeScript mirror in `workers/api/src/services/cashflow-runway.ts` and `app/webApp/src/api/cashflow-runway.ts`.
- [x] **Verification**: Unit tests simulating payday timing vs. credit card due dates and warning triggers when an impulse expense causes a future deficit (`CashflowRunwayEngineTest.kt` and `cashflow-runway.spec.ts`). All tests passing.

### Slice 3.3: Finance Cockpit UI (Runway Timeline & Pacing Bar)
- [x] Build `FinanceCockpitScreen`:
  - `Safe-to-Spend` hero metric and `Runway Alert Banner`.
  - Interactive 30/90-day Cashflow Runway timeline chart showing projected balance curve and deficit markers.
  - Dual-needle Pacing Budget bar (Time Needle vs. Spend Needle).
  - 14-day upcoming cashflow agenda list.
  - Subtab switching between `Overview (Cockpit)`, `Transactions (Ledger)`, and `Clearing Hub (Statements & Loans)`.
- [x] **Verification**: Compose UI tests in `:app:sharedUI:jvmTest` (`FinanceUiTest.kt`) and desktop verification (`./gradlew :app:desktopApp:test`). All tests passing.

---

## Phase 4: Server Persistence, Local-First Sync & Loan Optimization

**Objective**: Enable full multi-device synchronization through Cloudflare Workers + Supabase PostgreSQL and add advanced loan prepayment optimization.

### Slice 4.1: Supabase Migration & Worker Sync Endpoints
- [x] Add PostgreSQL migration in `supabase/migrations/20261105000000_add_finance_tables.sql` for all 8 `finance_*` tables with RLS policies, trigger-based `server_version` counters, and indexes.
- [x] Implement schema and HLC sync services in `workers/api/src/services/finance-sync-services.ts` and routes at `/api/sync/finance-*` in `workers/api/src/http/router.ts`.
- [x] Wire client `SyncEngine.kt`, `SyncRemoteApi.kt`, and `FinanceSyncStore.kt` to pull and push finance resources in topological dependency order (accounts -> exchange rates -> clearing groups -> transactions -> splits -> split clearing groups -> amortization plans -> budget rules).
- [x] **Verification**: `npm run api:test && npm run api:typecheck` + `:app:sharedLogic:jvmTest` sync tests (`FinanceSyncTest.kt`). All tests passing.

### Slice 4.2: Loan Prepayment Optimizer
- [x] Implement `LoanPrepaymentOptimizer` comparing **Term Reduction (缩短还款期限)** vs. **Installment Reduction (降低月供金额)** early partial repayment strategies across active loans, computing total interest saved, revised monthly payment, and shortened periods.
- [x] TypeScript mirror in `workers/api/src/services/loan-optimizer.ts` and `app/webApp/src/api/loan-optimizer.ts`.
- [x] **Verification**: Unit tests covering early payoff interest calculations and comparison metrics (`LoanPrepaymentOptimizerTest.kt` and `loan-optimizer.spec.ts`). All tests passing.

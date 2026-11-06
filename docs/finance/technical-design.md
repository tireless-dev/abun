# Finance Technical Design

## Purpose

This document defines the technical architecture, database schema, synchronization rules, and calculation invariants for the finance module. It aligns with Abun's shared local-first architecture ([Base Technical Architecture](../base/technical-architecture.md) and [Base Sync Architecture](../base/sync-architecture.md)).

---

## Monetary & Multi-Currency Representation Rule

To prevent floating-point rounding errors across Kotlin (JVM/Android/iOS/JS), SQLite, and TypeScript/PostgreSQL on Cloudflare Workers:

- **Storage (`amount_minor`)**: All monetary amounts are stored as **64-bit signed integers in the currency's minor units (`BIGINT` / `INTEGER`)** according to ISO 4217 exponent rules:
  - Standard 2-decimal currencies (`CNY`, `USD`, `EUR`, `HKD`, `GBP`): scaled by $10^2$ (`¥100.50` $\rightarrow$ `10050`).
  - Zero-decimal currencies (`JPY`, `KRW`): scaled by $10^0$ (`¥1000 JPY` $\rightarrow$ `1000`).
- **Cross-Currency Balancing Weights (`weight_amount_minor`, `weight_currency`)**:
  - Every `finance_split` stores both its native account amount (`amount_minor`, `currency`) and its transaction-balancing weight (`weight_amount_minor`, `weight_currency`).
  - For single-currency splits, `weight_amount_minor = amount_minor` and `weight_currency = currency`.
  - For cross-currency splits (Beancount `20.00 USD @@ 145.20 CNY`), `amount_minor = 2000`, `currency = 'USD'`, `weight_amount_minor = 14520`, `weight_currency = 'CNY'`.
- **Exchange Rates (`rate_micros`)**: Stored as 64-bit integers scaled by $10^6$ (e.g., `1 USD = 7.253400 CNY` $\rightarrow$ `7253400`).
- **Interest Rates (`rate_bps`)**: Stored as basis points (`10000` = `100.00%`, e.g., `4.50%` = `450`).

---

## Storage Model

The finance module introduces the following 8 synced tables in local SQLDelight (`AbunDatabase.sq`) and server PostgreSQL (`supabase/migrations/`):

1. `finance_account`
2. `finance_exchange_rate`
3. `finance_transaction`
4. `finance_split`
5. `finance_clearing_group`
6. `finance_split_clearing_group`
7. `finance_amortization_plan`
8. `finance_budget_rule`

### 1. `finance_account`

```sql
CREATE TABLE finance_account (
    id TEXT NOT NULL PRIMARY KEY,
    path TEXT NOT NULL,                     -- e.g. 'Assets:Bank:CMB'
    name TEXT NOT NULL,
    category TEXT NOT NULL,                 -- 'Asset' | 'Liability' | 'Income' | 'Expense' | 'Equity'
    currency TEXT NOT NULL DEFAULT 'CNY',
    include_in_net_worth INTEGER NOT NULL DEFAULT 1,
    is_pass_through INTEGER NOT NULL DEFAULT 0,
    pass_through_expense_account_id TEXT,   -- Optional linked Expense account for pass-through transfers
    liquid_tier TEXT NOT NULL DEFAULT 'Liquid', -- 'Liquid' | 'SemiLiquid' | 'Illiquid' | 'None'
    statement_day INTEGER,                  -- 1..31 for credit cards
    due_day INTEGER,                        -- 1..31 for credit cards
    is_archived INTEGER NOT NULL DEFAULT 0,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    -- HLC sync metadata per field group
    core_hlc TEXT NOT NULL,
    config_hlc TEXT NOT NULL,
    deleted_hlc TEXT NOT NULL,
    server_updated_at INTEGER NOT NULL DEFAULT 0,
    is_dirty INTEGER NOT NULL DEFAULT 0
);
```

### 2. `finance_transaction`

```sql
CREATE TABLE finance_transaction (
    id TEXT NOT NULL PRIMARY KEY,
    tx_date TEXT NOT NULL,                  -- 'YYYY-MM-DD'
    tx_time TEXT,                           -- Optional ISO 8601 timestamp
    payee TEXT,
    summary TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'Cleared', -- 'Draft' | 'Scheduled' | 'Cleared' | 'Void'
    source_plan_id TEXT,                    -- Optional FK to finance_amortization_plan.id
    period_index INTEGER,                   -- Optional 1..N installment index
    is_deleted INTEGER NOT NULL DEFAULT 0,
    -- HLC sync metadata
    header_hlc TEXT NOT NULL,
    status_hlc TEXT NOT NULL,
    deleted_hlc TEXT NOT NULL,
    server_updated_at INTEGER NOT NULL DEFAULT 0,
    is_dirty INTEGER NOT NULL DEFAULT 0
);
```

### 3. `finance_split`

```sql
CREATE TABLE finance_split (
    id TEXT NOT NULL PRIMARY KEY,
    transaction_id TEXT NOT NULL REFERENCES finance_transaction(id) ON DELETE CASCADE,
    account_id TEXT NOT NULL REFERENCES finance_account(id),
    amount_minor INTEGER NOT NULL,          -- Signed minor units in account's native currency
    currency TEXT NOT NULL DEFAULT 'CNY',
    weight_amount_minor INTEGER NOT NULL,   -- Balancing weight minor units (equals amount_minor when currency == weight_currency)
    weight_currency TEXT NOT NULL DEFAULT 'CNY',
    narration TEXT NOT NULL DEFAULT '',     -- Split-level narration
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    -- HLC sync metadata
    content_hlc TEXT NOT NULL,
    deleted_hlc TEXT NOT NULL,
    server_updated_at INTEGER NOT NULL DEFAULT 0,
    is_dirty INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_finance_split_tx ON finance_split(transaction_id) WHERE is_deleted = 0;
CREATE INDEX idx_finance_split_account ON finance_split(account_id) WHERE is_deleted = 0;
```

### 4. `finance_clearing_group` & `finance_split_clearing_group`

```sql
CREATE TABLE finance_clearing_group (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    group_type TEXT NOT NULL,               -- 'InstantBatch' | 'CreditStatement' | 'LoanLifecycle' | 'Reimbursement'
    anchor_currency TEXT NOT NULL DEFAULT 'CNY', -- Settlement currency of this group
    target_account_id TEXT,                 -- Optional primary account (e.g. credit card or loan account)
    statement_cycle TEXT,                   -- Optional 'YYYY-MM' for CreditStatement groups
    due_date TEXT,                          -- Optional 'YYYY-MM-DD'
    status TEXT NOT NULL DEFAULT 'Active',  -- 'Active' | 'Settled' | 'Overdrawn'
    settled_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    -- HLC sync metadata
    core_hlc TEXT NOT NULL,
    status_hlc TEXT NOT NULL,
    deleted_hlc TEXT NOT NULL,
    server_updated_at INTEGER NOT NULL DEFAULT 0,
    is_dirty INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE finance_split_clearing_group (
    id TEXT NOT NULL PRIMARY KEY,           -- Deterministic: "${split_id}:${clearing_group_id}"
    split_id TEXT NOT NULL REFERENCES finance_split(id) ON DELETE CASCADE,
    clearing_group_id TEXT NOT NULL REFERENCES finance_clearing_group(id) ON DELETE CASCADE,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    deleted_hlc TEXT NOT NULL,
    server_updated_at INTEGER NOT NULL DEFAULT 0,
    is_dirty INTEGER NOT NULL DEFAULT 0,
    UNIQUE(split_id, clearing_group_id)
);

CREATE INDEX idx_split_cg_group ON finance_split_clearing_group(clearing_group_id) WHERE is_deleted = 0;
CREATE INDEX idx_split_cg_split ON finance_split_clearing_group(split_id) WHERE is_deleted = 0;
```

### 5. `finance_amortization_plan`

```sql
CREATE TABLE finance_amortization_plan (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    principal_minor INTEGER NOT NULL,
    currency TEXT NOT NULL DEFAULT 'CNY',
    total_periods INTEGER NOT NULL,
    repayment_method TEXT NOT NULL,         -- 'EqualInstallment' | 'EqualPrincipal' | 'InterestFirst' | 'FlatFee'
    annual_rate_bps INTEGER NOT NULL DEFAULT 0,
    fee_per_period_minor INTEGER NOT NULL DEFAULT 0,
    start_date TEXT NOT NULL,
    repayment_day INTEGER NOT NULL,
    liability_account_id TEXT NOT NULL REFERENCES finance_account(id),
    funding_account_id TEXT REFERENCES finance_account(id),
    interest_account_id TEXT NOT NULL REFERENCES finance_account(id),
    lifecycle_clearing_group_id TEXT NOT NULL REFERENCES finance_clearing_group(id),
    is_deleted INTEGER NOT NULL DEFAULT 0,
    core_hlc TEXT NOT NULL,
    deleted_hlc TEXT NOT NULL,
    server_updated_at INTEGER NOT NULL DEFAULT 0,
    is_dirty INTEGER NOT NULL DEFAULT 0
);
```

### 6. `finance_budget_rule`

```sql
CREATE TABLE finance_budget_rule (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    scope_type TEXT NOT NULL,               -- 'GlobalDiscretionary' | 'AccountSubtree'
    account_prefix TEXT NOT NULL DEFAULT 'Expenses:',
    excluded_prefixes_json TEXT NOT NULL DEFAULT '[]',
    monthly_limit_minor INTEGER NOT NULL,
    currency TEXT NOT NULL DEFAULT 'CNY',
    include_in_runway INTEGER NOT NULL DEFAULT 1,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    core_hlc TEXT NOT NULL,
    deleted_hlc TEXT NOT NULL,
    server_updated_at INTEGER NOT NULL DEFAULT 0,
    is_dirty INTEGER NOT NULL DEFAULT 0
);
```

### 7. `finance_exchange_rate`

```sql
CREATE TABLE finance_exchange_rate (
    id TEXT NOT NULL PRIMARY KEY,           -- Deterministic: "fx:${base_currency}:${quote_currency}:${rate_date}"
    base_currency TEXT NOT NULL,            -- e.g. 'USD'
    quote_currency TEXT NOT NULL,           -- e.g. 'CNY'
    rate_date TEXT NOT NULL,                -- 'YYYY-MM-DD'
    rate_micros INTEGER NOT NULL,           -- Rate scaled by 10^6 (e.g. 7250000 for 7.25)
    source TEXT NOT NULL DEFAULT 'Manual',  -- 'Manual' | 'ImplicitFromSplit' | 'RemoteFeed'
    is_deleted INTEGER NOT NULL DEFAULT 0,
    core_hlc TEXT NOT NULL,
    deleted_hlc TEXT NOT NULL,
    server_updated_at INTEGER NOT NULL DEFAULT 0,
    is_dirty INTEGER NOT NULL DEFAULT 0,
    UNIQUE(base_currency, quote_currency, rate_date)
);

CREATE INDEX idx_fx_lookup ON finance_exchange_rate(base_currency, quote_currency, rate_date DESC) WHERE is_deleted = 0;
```

---

## Deterministic ID Rules

Following Abun's cross-device idempotency guidelines:

1. **Monthly Credit Card Statement Group ID**:
   - Format: `cg-stmt:${accountId}:${yyyy}-${mm}`
   - Ensures that if two offline devices both record a credit card expense in May 2026 and auto-create the May 2026 statement group, both devices generate the exact same `ClearingGroup.id` and merge cleanly during sync.
2. **Split-to-ClearingGroup Junction ID**:
   - Format: `scg:${splitId}:${clearingGroupId}`
   - Prevents duplicate links when syncing many-to-many bindings.
3. **Exchange Rate Record ID**:
   - Format: `fx:${baseCurrency}:${quoteCurrency}:${rateDate}`
4. **Amortization Generated Scheduled Records**:
   - Transaction ID: `tx-plan:${planId}:p${periodIndex}`
   - Principal Split ID: `sp-plan:${planId}:p${periodIndex}:principal`
   - Interest Split ID: `sp-plan:${planId}:p${periodIndex}:interest`
   - Funding Split ID: `sp-plan:${planId}:p${periodIndex}:funding`

---

## Local Write & Validation Invariants

### 1. Transaction Commit Validation (with Multi-Currency Weights)
Before a `Transaction` can be saved with `status = 'Cleared'` or `'Scheduled'`:
- Count of active (`is_deleted = 0`) splits must be $\ge 2$.
- Every split must satisfy `sign(amount_minor) == sign(weight_amount_minor)`.
- For every distinct `weight_currency` $c_w$ among its active splits:
  $$\sum_{s \in \text{splits}(tx, c_w)} s.\text{weight\_amount\_minor} = 0$$
- If any condition fails, the transaction must be rejected or explicitly saved with `status = 'Draft'`.

### 2. Automatic Statement Group Assignment
During local write of a `Split` on an `Account` where `category = 'Liability'` and `statement_day IS NOT NULL`:
- If the split is an expense/refund (not an explicit statement repayment already bound to a prior statement) and no `CreditStatement` group is manually specified:
  - Compute the billing cycle `YYYY-MM` from `Transaction.tx_date` and `Account.statement_day` (if `dayOfMonth(tx_date) <= statement_day`, cycle is current month `YYYY-MM`; otherwise next month `YYYY-(MM+1)`).
  - Upsert deterministic `ClearingGroup` `cg-stmt:${accountId}:${cycle}` with `anchor_currency = Account.currency` and `due_date` computed from `Account.due_day`.
  - Bind the split via `finance_split_clearing_group`.

### 3. Clearing Group Balance & Status Refresh
Whenever a `Split` or `SplitClearingGroup` is created, modified, or soft-deleted in a local SQLite transaction, recompute the affected `ClearingGroup` using its `anchor_currency`:

```sql
SELECT
    COALESCE(SUM(
        CASE
            WHEN t.status != 'Cleared' THEN 0
            WHEN s.currency = cg.anchor_currency THEN s.amount_minor
            WHEN s.weight_currency = cg.anchor_currency THEN s.weight_amount_minor
            ELSE 0
        END
    ), 0) AS realized_balance,
    COUNT(CASE WHEN t.status = 'Cleared' THEN 1 END) AS cleared_count
FROM finance_clearing_group cg
JOIN finance_split_clearing_group scg ON scg.clearing_group_id = cg.id AND scg.is_deleted = 0
JOIN finance_split s ON s.id = scg.split_id AND s.is_deleted = 0
JOIN finance_transaction t ON t.id = s.transaction_id AND t.is_deleted = 0
WHERE cg.id = :groupId;
```

- For `InstantBatch`, `LoanLifecycle`, and `Reimbursement`:
  - If `cleared_count >= 2` and `realized_balance == 0` $\rightarrow$ `status = 'Settled'`.
  - If `realized_balance > 0` (for liability groups where debt is negative) $\rightarrow$ `status = 'Overdrawn'`.
  - Otherwise $\rightarrow$ `status = 'Active'`.
- For `CreditStatement`:
  - Transitions to `'Settled'` when `realized_balance == 0` AND (`today > statement_closing_date` OR an explicit repayment split `+amount` is present).

---

## Sync Rules

### Resource Ordering
To respect foreign-key dependencies during pull-then-push sync, extend [`SyncEngine.kt`](file:///Users/jerry/repos/abun/app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/SyncEngine.kt) resource ordering with:

1. `finance_account`
2. `finance_exchange_rate`
3. `finance_clearing_group`
4. `finance_amortization_plan`
5. `finance_transaction`
6. `finance_split`
7. `finance_split_clearing_group`
8. `finance_budget_rule`

### Conflict Resolution
- All 7 tables use Abun's standard **field-group Last-Write-Wins with Hybrid Logical Clock (HLC)** tokens.
- If a remote sync pull updates a `finance_split`, the client runs the local post-merge hook to recompute the derived status of any linked `finance_clearing_group`.

---

## Implementation Status (Reviewed 2026-11-06)

- **Phase 1 Complete (Double-Entry Core & Clearing Engine)**:
  - Pure arithmetic, ISO 4217 scales, cross-currency balancing weights (`weight_amount_minor`, `weight_currency`), validation logic (`FinanceValidators`), and clearing group evaluation (`ClearingGroupEvaluator`) implemented in `core`.
  - Local SQLDelight tables (`finance_account`, `finance_exchange_rate`, `finance_transaction`, `finance_split`, `finance_clearing_group`, `finance_split_clearing_group`) and atomic transactions (`FinanceStore.kt`) implemented and tested on JVM SQLite.
  - UI layer (`app/sharedUI`) complete with top-level `Finance` navigation destination, `FinanceLedgerScreen` (Standard view & Beancount plain-text ledger view), `CreateFinanceTransactionSheet` (fast 2-split and expandable multi-split with auto-balance calculation), and `FinanceAccountsSheet` (account grouping and Auto-Pad reconciliation).

- **Phase 2 Complete (Credit Card Statements, Combined Repayments & Installment Generator)**:
  - Deterministic credit card statement cycle calculation (`StatementCycleEngine`), leap-year clamping, auto-clearing group tagging (`cg-stmt:${accountId}:${cycle}`), and statement cycle reassignment.
  - Amortization schedule generator (`AmortizationEngine`) supporting Equal Installment, Equal Principal, Flat Fee, and Interest First with deterministic scheduled transaction synthesis.
  - UI layer complete with `ClearingHubScreen` (Credit card statements with one-click repay, loan progress cards, instant batches), `CombinedRepaymentSheet` (multi-item repayment with custom bank funding splits), and `AmortizationWizardSheet`.

- **Phase 3 Complete (Guardrail Pacing Budget & Cashflow Runway Cockpit)**:
  - Guardrail pacing budget evaluator (`BudgetEvaluator`) with multi-currency normalization, account subtree filtering, and time-vs-spend pacing delta.
  - 90-day Cashflow Runway projector (`CashflowRunwayEngine`) computing daily liquidity curve, `SafeToSpend`, deficit markers, and minimum cash troughs.
  - UI layer complete with `FinanceCockpitScreen` featuring hero `Safe-to-Spend`, Runway Radar banner, interactive 30/60/90-day trajectory chart, dual-needle Pacing bar, and 14-day upcoming cashflow agenda.

- **Phase 4 Complete (Server Persistence, Local-First Sync & Loan Prepayment Optimizer)**:
  - PostgreSQL schema with RLS and trigger-based versioning (`supabase/migrations/20261105000000_add_finance_tables.sql`).
  - Cloudflare Worker D1/in-memory sync services (`workers/api/src/services/finance-sync-services.ts`) and HTTP routes (`/api/sync/finance-*`).
  - Client-side `SyncEngine.kt`, `SyncRemoteApi.kt`, and `FinanceSyncStore.kt` orchestrating topological pull and push sync with field-level HLC conflict resolution.
  - Loan Prepayment Optimizer (`LoanPrepaymentOptimizer`) simulating early payoff impacts comparing Term Reduction vs. Installment Reduction strategies.
  - 100% test passing across `:core:jvmTest`, `:app:sharedLogic:jvmTest`, `:app:sharedUI:jvmTest`, `:app:desktopApp:test`, `npm run api:test`, `npm run api:typecheck`, and `npm run test:web`.

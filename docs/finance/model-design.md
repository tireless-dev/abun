# Finance Model Design

## Purpose

This document defines the domain model for the finance module. It combines the mathematical rigor of Beancount-style double-entry bookkeeping with first-class split narrations, zero-sum clearing groups, guardrail pacing budgets, and forward-looking cashflow runway projection.

## Module Scope

The finance module encompasses:

- hierarchical double-entry accounts (`Account`) with per-account native currency
- two-layer transactions and splits (`Transaction`, `Split`) with split-level narration and Beancount-style cross-currency balancing weights (`@@` / `@`)
- multi-currency exchange rate history (`ExchangeRate` / Beancount `price` directive equivalent)
- multi-dimensional zero-sum clearing groups (`ClearingGroup`, `SplitClearingGroup`) supporting cross-currency settlement (e.g., USD spend settled via CNY auto-FX repayment)
- loan and installment amortization plans (`AmortizationPlan`)
- guardrail pacing budgets (`BudgetRule`) with automatic base-currency normalization
- derived financial safety metrics (`SafeToSpend`, `CashflowRunway`)

## Core Domain Position

1. **Double-entry ledger as the source of truth**: Every committed financial event is represented as a `Transaction` containing two or more `Split` entries whose balancing weights sum to zero ($\sum \text{weight} = 0$).
2. **First-class multi-currency (`@@` total cost / `@` rate)**: Inspired by Beancount's posting weight model, each `Split` records its native account `amount` + `currency`, and optionally a `weight_amount` + `weight_currency` for cross-currency transactions (such as foreign-currency card swipes, currency exchange, or cross-currency reimbursement).
3. **First-class split narration**: Unlike plain-text Beancount where narration only exists at the transaction header, every `Split` carries its own `narration` field to describe individual line items, multi-card deductions, or combined repayments.
4. **Zero-sum clearing contracts**: Instead of loose string tags or links, cross-transaction matching (loans, monthly credit card statements, merged repayments, reimbursements) is modeled via `ClearingGroup`, which enforces and tracks that its constituent splits sum to zero ($\sum_{s \in G} \text{effective\_amount}(s) = 0$) in the group's settlement currency.
5. **Forward-looking cashflow safety**: Past ledger truth is combined with future scheduled splits and budget burn rates to answer *"How much can I safely spend today?"* and *"When might my cashflow run dry?"*

---

## Entities

### 1. Account

Represents a bucket in the five standard accounting categories (`Asset`, `Liability`, `Income`, `Expense`, `Equity`), organized hierarchically via colon-separated paths (e.g., `Assets:Bank:CMB`, `Liabilities:CreditCard:CMB`, `Expenses:Food:Dining`).

Core fields:

- `id`: stable UUID
- `path`: hierarchical path string (e.g., `Assets:Bank:CMB`)
- `name`: display name
- `category`: `Asset` | `Liability` | `Income` | `Expense` | `Equity`
- `currency`: ISO 4217 currency code (default `CNY`)
- `include_in_net_worth`: boolean (default `true`)
- `is_pass_through`: boolean (default `false`)
- `liquid_tier`: `Liquid` | `SemiLiquid` | `Illiquid` | `None` (used to identify available cash pools for runway calculation)
- `statement_day`: optional day of month (`1..31`) for credit card / billing accounts
- `due_day`: optional day of month (`1..31`) or offset days after `statement_day`
- `is_archived`: boolean
- `is_deleted`: boolean

Special account semantics:

- **Excluded from net worth (`include_in_net_worth = false`)**: Balances in this account are omitted from personal net worth and wealth reports.
- **Pass-through / direct-consumption (`is_pass_through = true`)**: Used for petty-cash containers such as meal cards, transit cards, or pocket money pools. Transfers into a pass-through account are immediately recognized as consumption against budgets and cashflow without requiring the user to log every micro-purchase inside the card.

---

### 2. Transaction

Represents the atomic boundary and shared envelope of a financial event.

Core fields:

- `id`: stable UUID
- `tx_date`: ISO 8601 local date (`YYYY-MM-DD`)
- `tx_time`: optional ISO 8601 timestamp with timezone
- `payee`: counterparty or merchant name (optional)
- `summary`: transaction-level overall description
- `status`: `Draft` | `Scheduled` | `Cleared` | `Void`
- `source_plan_id`: optional reference to an `AmortizationPlan` or recurring generator
- `is_deleted`: boolean

Lifecycle state rules:

| Status | Split Count Requirement | Zero-Sum Requirement ($\sum \text{weight} = 0$) | Affects Actual Balances | Affects Cashflow Forecast |
| :--- | :--- | :--- | :--- | :--- |
| **`Draft`** | $0, 1, \text{or } N$ | Not enforced | No | No (shown in Inbox) |
| **`Scheduled`** | $\ge 2$ | Enforced per `weight_currency` | No (future/pending) | **Yes** |
| **`Cleared`** | $\ge 2$ | Enforced per `weight_currency` | **Yes** | **Yes** (as realized cash) |
| **`Void`** | Any | Ignored | No | No |

---

### 3. Split (Posting)

Represents a signed value movement on a single `Account` within a `Transaction`, with native support for Beancount-style cross-currency conversion weights (`@@` total cost / `@` unit price).

Sign convention (standard Beancount / accounting sign rules):

- `Asset` & `Expense`: positive (`+`) increases balance/spending, negative (`-`) decreases
- `Liability`, `Equity`, & `Income`: negative (`-`) increases liability/income, positive (`+`) decreases/repays liability

Core fields:

- `id`: stable UUID
- `transaction_id`: FK to `Transaction.id`
- `account_id`: FK to `Account.id`
- `amount`: fixed-point decimal in the account's native currency
- `currency`: ISO 4217 code (must match `Account.currency`)
- `weight_amount`: balancing weight amount (defaults to `amount` when not converting currencies; for cross-currency splits like `20.00 USD @@ 145.20 CNY`, `amount = 20.00`, `currency = USD`, `weight_amount = 145.20`, `weight_currency = CNY`)
- `weight_currency`: ISO 4217 code used when balancing the parent `Transaction` (defaults to `currency`)
- `narration`: split-specific description (e.g., `"招行卡扣款"`, `"偿还A贷款第5期本金"`, `"ChatGPT Plus $20 购汇入账"`)
- `sort_order`: integer ordering within the transaction
- `is_deleted`: boolean

Cross-currency & atomic split rules:

- **Sign consistency**: `weight_amount` must always have the same sign (`+`, `-`, or `0`) as `amount`.
- **Transaction balancing**: A `Transaction` balances when, for every distinct `weight_currency` across its splits, $\sum \text{weight\_amount} = 0$.
- **Atomic split participation**: A `Split` participates in any bound `ClearingGroup` as an indivisible unit. If `ClearingGroup.anchor_currency == Split.currency`, it contributes `Split.amount`; if `ClearingGroup.anchor_currency == Split.weight_currency`, it contributes `Split.weight_amount`.

---

### 4. ClearingGroup & SplitClearingGroup

Represents a zero-sum settlement contract across multiple splits and transactions.

Core fields (`ClearingGroup`):

- `id`: stable ID (random UUID for ad-hoc groups; deterministic ID for auto-derived monthly statement groups)
- `name`: human-readable label (e.g., `"招行信用卡 2026-04 账单"`, `"A机构装修贷本金对冲"`, `"2026-05-20 合并还款批次"`)
- `group_type`:
  - `InstantBatch`: same-day multi-account settlement or merged repayment batch
  - `CreditStatement`: monthly credit card billing cycle
  - `LoanLifecycle`: long-term loan or installment principal clearance
  - `Reimbursement`: advance payment awaiting reimbursement or AA split settlement
- `anchor_currency`: ISO 4217 settlement currency for this group (e.g., `CNY` or `USD`)
- `target_account_id`: optional primary liability/receivable account associated with the group
- `due_date`: optional ISO 8601 date when the group (or its current cycle) must be settled
- `status`: `Active` | `Settled` | `Overdrawn`
- `settled_at`: optional timestamp when net balance reached `0`
- `is_deleted`: boolean

Junction entity (`SplitClearingGroup`):

- `id`: deterministic or composite key (`split_id`, `clearing_group_id`)
- `split_id`: FK to `Split.id`
- `clearing_group_id`: FK to `ClearingGroup.id`
- `is_deleted`: boolean

Multi-group & cross-currency settlement semantics:

1. **Orthogonal multi-group intersection**: A single `Split` can belong to multiple orthogonal `ClearingGroup`s simultaneously (e.g., both a `LoanLifecycle` group and an `InstantBatch` combined repayment group).
2. **Cross-currency statement / reimbursement clearing**:
   - *Scenario A (USD spend billed in CNY via Auto-FX)*: Swiping `$20.00 USD @@ 145.20 CNY` on a dual-currency card creates a split with `amount = -20.00 USD` and `weight_amount = -145.20 CNY`. When bound to a `CNY`-anchored statement group, it contributes `-145.20 CNY`, which is later settled to `0` by a `+145.20 CNY` repayment.
   - *Scenario B (Native USD statement settled via CNY purchase)*: Swiping `$100.00 USD` accumulates `-100.00 USD` in a `USD`-anchored statement group. On repayment day, paying from a `CNY` debit card (`-725.00 CNY`) into the `USD` credit card (`+100.00 USD @@ 725.00 CNY`) settles the `USD` statement group (`-100 + 100 = 0 USD`) while simultaneously balancing the `CNY` transaction (`-725 + 725 = 0 CNY`)!

Derived balance and status rules for a `ClearingGroup` $G$ with `anchor_currency` $C_G$:

Let $\text{eff}(s, C_G) = s.\text{amount}$ if $s.\text{currency} = C_G$, else $s.\text{weight\_amount}$ (where $s.\text{weight\_currency} = C_G$).

- **Realized Balance**:
  $$\text{Balance}_{\text{realized}}(G) = \sum_{s \in G, \text{tx}(s) = \text{Cleared}} \text{eff}(s, C_G)$$
- **Projected Balance**:
  $$\text{Balance}_{\text{projected}}(G) = \sum_{s \in G, \text{tx}(s) \in \{\text{Cleared}, \text{Scheduled}\}} \text{eff}(s, C_G)$$
- When $\text{Balance}_{\text{realized}}(G) = 0$ (and the group has at least 2 splits and is past its accumulation window for statements), its derived/stored status transitions to `Settled`.
- If a repayment exceeds the debt ($\text{Balance}_{\text{realized}}(G) > 0$ for a liability group), status is flagged as `Overdrawn`.

---

### 5. AmortizationPlan

Defines a generator contract for loans, credit card installments, or recurring debt schedules.

Core fields:

- `id`: stable UUID
- `name`: plan title (e.g., `"MacBook Pro 12期分期"`, `"消费贷A款"`)
- `principal_amount`: total principal
- `total_periods`: integer ($N$ periods)
- `repayment_method`: `EqualInstallment` (等额本息) | `EqualPrincipal` (等额本金) | `InterestFirst` (先息后本) | `FlatFee` (分期固定手续费)
- `annual_interest_rate`: optional decimal APR/nominal rate
- `fee_per_period`: optional fixed fee per period
- `start_date`: ISO 8601 date
- `repayment_day`: day of month (`1..31`)
- `liability_account_id`: FK to `Account.id`
- `funding_account_id`: optional default FK to `Account.id` for scheduled repayments
- `interest_expense_account_id`: FK to `Account.id` (e.g., `Expenses:Finance:Interest`)
- `lifecycle_clearing_group_id`: FK to the `ClearingGroup` tracking principal clearance (`-Principal` + $\sum_{i=1}^N +\text{Principal}_i = 0$)
- `is_deleted`: boolean

---

### 6. BudgetRule (Guardrail Pacing Budget)

Represents a top-down spending guardrail over the `Expense` account hierarchy that also supplies the daily discretionary burn rate for cashflow runway projection.

Core fields:

- `id`: stable UUID
- `name`: label (e.g., `"日常弹性总预算"`, `"餐饮外卖限额"`)
- `scope_type`: `GlobalDiscretionary` (all flexible expenses) | `AccountSubtree` (specific prefix like `Expenses:Food`)
- `account_prefix`: optional path prefix matched against `Account.path`
- `excluded_account_prefixes`: list of prefixes excluded from this guardrail (e.g., `Expenses:Finance:Interest`, `Expenses:Housing:Rent`)
- `monthly_limit`: decimal target limit per month
- `currency`: ISO 4217 currency of the budget (defaults to user's `base_currency`, e.g., `CNY`)
- `include_in_runway`: boolean (whether remaining unspent budget for future days is subtracted as expected daily living burn in Cashflow Runway)
- `is_deleted`: boolean

---

### 7. ExchangeRate (Price Directive)

Equivalent to Beancount's `price` directive (`YYYY-MM-DD price USD 7.25 CNY`). Provides reference exchange rates for valuing multi-currency accounts, normalizing foreign-currency expenses into `BudgetRule`s when no explicit transaction `weight_currency` matches, and projecting multi-currency obligations in `CashflowRunway`.

Core fields:

- `id`: deterministic ID (`fx:${base_currency}:${quote_currency}:${rate_date}`)
- `base_currency`: ISO 4217 code (e.g., `USD`)
- `quote_currency`: ISO 4217 code (e.g., `CNY`)
- `rate_date`: ISO 8601 date (`YYYY-MM-DD`)
- `rate_micros`: integer rate scaled by $10^6$ (e.g., `7.250000` is stored as `7250000`)
- `source`: `Manual` | `ImplicitFromSplit` (auto-recorded when a user enters a cross-currency `@@` split) | `RemoteFeed`
- `is_deleted`: boolean

Multi-currency valuation priority:

When normalizing a `Split` or `Account` balance into target currency $C_{\text{target}}$ (such as the user's primary `base_currency` for `BudgetRule` or `CashflowRunway`):

1. **Direct match**: If `split.currency == C_target`, use `split.amount`.
2. **Transaction weight match**: If `split.weight_currency == C_target`, use `split.weight_amount` (preserves the exact historical FX cost locked in at transaction time).
3. **Latest `ExchangeRate` lookup**: Otherwise, multiply `split.amount` by the most recent `ExchangeRate(split.currency -> C_target)` on or before `tx_date`.

---

## Derived Financial Metrics

### 1. Pacing Status (for any `BudgetRule` in month $M$)

- **Time Elapsed Ratio**: $R_{\text{time}} = \frac{\text{current\_day}}{\text{days\_in\_month}}$
- **Budget Spent Ratio**: $R_{\text{spent}} = \frac{\text{Spent}_M}{\text{monthly\_limit}}$ (where $\text{Spent}_M$ is normalized into `BudgetRule.currency` using the multi-currency valuation priority above)
- **Pacing Delta**: $\Delta_{\text{pace}} = \text{Spent}_M - (\text{monthly\_limit} \times R_{\text{time}})$
  - $\Delta_{\text{pace}} \le 0$: On track (spending needle is behind or equal to time needle).
  - $\Delta_{\text{pace}} > 0$: Over-pacing (spending faster than calendar progression).

### 2. Safe-to-Spend (真实可支配现金)

Unlike raw bank balances, `SafeToSpend` protects the user from spending money that is already committed to upcoming bills (all normalized into user's `base_currency`, with optional per-currency breakdown):

$$\text{SafeToSpend}(t) = \text{LiquidAssets}(t) - \text{CommittedOutflows}(t, t + W) - \text{RemainingEssentialBudget}(t)$$

Where:
- $\text{LiquidAssets}(t)$: Sum of balances of `Account`s with `liquid_tier = Liquid` and `include_in_net_worth = true`.
- $\text{CommittedOutflows}(t, t + W)$: Unsettled obligations in `Scheduled` transactions and active `ClearingGroup`s due within window $W$ (default: until next primary income date or rolling 30 days).
- $\text{RemainingEssentialBudget}(t)$: Unspent portion of `GlobalDiscretionary` budget reserved for daily living in the current cycle.

### 3. Cashflow Runway & Deficit Date (现金流跑道与断粮预警)

For any future date $D > \text{today}$, the projected liquid cash balance is:

$$\text{ProjectedCash}(D) = \text{LiquidAssets}(\text{today}) + \sum_{d = \text{today}+1}^{D} \Big( \text{ScheduledInflows}(d) - \text{ScheduledOutflows}(d) - \text{ExpectedDiscretionaryBurn}(d) \Big)$$

Where $\text{ExpectedDiscretionaryBurn}(d)$ is derived from the active `GlobalDiscretionary` budget rule ($\frac{\text{remaining\_monthly\_budget}}{\text{remaining\_days}}$ for the current month, and $\frac{\text{monthly\_limit}}{\text{days\_in\_month}}$ for future months).

- **Runway Deficit Date**: The earliest date $D_{\text{deficit}}$ where $\text{ProjectedCash}(D_{\text{deficit}}) < 0$.
- **Minimum Cash Trough**: $\min_{D \in [\text{today}, \text{today}+90]} \text{ProjectedCash}(D)$ and the date on which it occurs.

# Finance Functionality Design

## Purpose

This document describes the user-facing behavior of the finance module. It serves as the PRD-level reference for how everyday bookkeeping, statement/loan clearing, installment generation, pacing budgets, and cashflow runway forecasting work from a user perspective.

Design philosophy:

- **Mathematical rigor underneath, zero accounting jargon on the surface**: Everyday actions use intuitive task-like workflows (`Spend`, `Income`, `Transfer / Repay`, `Borrow / Installment`), while preserving a full Beancount-style double-entry ledger switch for inspection and auditing.
- **Zero-friction grouping**: 90% of `ClearingGroup` bindings (such as monthly credit card statements) happen automatically via account billing rules rather than manual tag picking.
- **Forward-looking peace of mind**: The primary dashboard answers *"How much can I safely spend today?"* and *"Will I run short on any upcoming bill date?"* before showing historical charts.

---

## Primary User Jobs

1. **See true financial safety at a glance**: Know `Safe-to-Spend` cash today and whether any future repayment date in the next 90 days faces a liquidity deficit.
2. **Capture transactions with split-level clarity**: Log simple expenses in seconds, or split a complex transaction across multiple accounts and categories with independent narration on every split.
3. **Manage loans, credit card statements, and combined repayments effortlessly**: Group multiple loan tranches or monthly bills and settle them via single or multi-card transfers that mathematically verify to zero.
4. **Batch-generate future installments**: Turn a large purchase, bill, or loan into $N$ scheduled future payments in one wizard flow.
5. **Eliminate petty-cash fatigue**: Transfer money into pass-through accounts (meal cards, transit cards, pocket money) as immediate consumption without logging micro-transactions.
6. **Control spending pace without envelope micromanagement**: Track coarse-grained monthly spending guardrails with a time-vs-spend pacing indicator.
7. **Recover from missed entries in one click**: Use balance assertion (`Auto-Pad`) to reconcile account drift without hunting down forgotten small expenses.

---

## Product Surfaces

### 1. Finance Overview (Runway & Cockpit)

The primary landing surface for the finance module.

Sections:

1. **Safety Header (`Safe-to-Spend` & `Runway Status`)**:
   - **Primary Metric**: `Safe-to-Spend` (真实可支配现金) — liquid cash minus upcoming committed bills/loans and remaining living budget.
   - **Runway Radar Banner**:
     - *Healthy*: `"90-day cashflow healthy. Lowest cash trough: ¥4,200 on Jun 20."`
     - *Warning / Deficit*: `"⚠️ Cash shortfall of -¥1,450 projected on Jun 20 (before Jun 25 payday)."`
2. **Cashflow Runway Timeline (30 / 90 Days)**:
   - Interactive step-line chart and chronological agenda of upcoming `Scheduled` inflows, credit card due dates, loan installments, and projected daily living burn.
3. **Pacing Budget Bar (护栏配速条)**:
   - Single unified progress bar for `GlobalDiscretionary` budget (plus 1–3 optional watched category guardrails) showing **Time Needle** (e.g., `Day 10 / 33%`) vs. **Spend Needle** (e.g., `¥1,800 / 40%`).
4. **Upcoming Obligations & Unsettled Clearing Groups**:
   - Actionable cards for bills due soon, active `InstantBatch` groups with non-zero discrepancy, and pending `Draft` transactions in the Inbox.

---

### 2. Ledger & Transactions Surface

The chronological record and management surface for transactions.

Capabilities:

- **Dual Presentation Mode**:
  - **Standard Mode**: Clean editorial cards showing payee, summary, net impact, account badges, and clearing group pills.
  - **Ledger / Beancount Mode**: Expandable multi-line view showing every `Split`, its signed amount, account path, split-level `narration`, and bound `ClearingGroup` badges.
- **Primary Filters**:
  - `All`
  - `Drafts (Inbox)` (incomplete transactions with $<2$ splits or unbalanced amounts)
  - `Scheduled (Future)`
  - `By Account`
  - `By Clearing Group`

---

### 3. Clearing & Debt Hub (`ClearingGroups` & `Loans`)

Dedicated surface for managing zero-sum settlement contracts.

Subtabs:

1. **Statements & Batches (`CreditStatement` & `InstantBatch`)**:
   - Monthly credit card statement cards showing accumulated spend, refunds, due date, and a primary **`[Repay Statement]`** button.
   - Same-day combined repayment batches (`InstantBatch`). If a batch's splits do not sum to `0`, an explicit warning badge highlights the exact unallocated discrepancy.
2. **Loans & Installments (`LoanLifecycle`)**:
   - Progress cards for active loans and credit card installments (e.g., `"Paid 5/12 periods · ¥15,000 / ¥36,000 · Remaining: ¥21,000"`).
   - Loan Optimization Simulator: Compare prepayment impact across active loans (Term Reduction vs. Installment Reduction strategies).
3. **Reimbursements & Shared (`Reimbursement`)**:
   - Open receivables/advances awaiting reimbursement or settlement.

---

### 4. Accounts & Reconciliation Surface

- Tree view of `Assets`, `Liabilities`, `Income`, `Expenses`, and `Equity`.
- Quick badges indicating `Pass-through (转入即消费)` and `Excluded from Net Worth (不计入净资产)`.
- **`[Reconcile / Auto-Pad]`** action on any asset or liability account.

---

## Core Workflows

### 1. Create Transaction (Simple & Multi-Split)

User outcome:

- Users can log a fast 2-split transaction in under 5 seconds, or expand into multi-split mode for complex events.

Behavior:

1. **Quick Entry Mode**:
   - Choose intent tab: `Expense` | `Income` | `Transfer / Repay`.
   - Enter `Amount`, `From Account`, `To Account / Category`, `Summary`, and optional split `Narration`.
   - **Cross-Currency Auto-Prompt (`@@` / `@`)**: If `From Account` and `To Account` have different currencies (e.g., paying `20.00 USD` using a `CNY` card, or transferring `CNY` to `HKD`), an inline conversion field appears allowing the user to enter either **Total Cost (`@@ 145.20 CNY`)** or **Exchange Rate (`@ 7.26`)**. Entering a cross-currency transaction also automatically records an `ImplicitFromSplit` entry in `ExchangeRate` history.
   - System automatically constructs the two opposing `Split` rows with matching balancing weights (`-145.20 CNY` and `+20.00 USD @@ 145.20 CNY`).
2. **Multi-Split Mode (`+ Add Split`)**:
   - Add arbitrary split rows. Each row has `Account`, signed `Amount` (in the account's currency), optional `Weight Amount & Currency` (`@@`), `Narration` (分录独立描述), and optional `ClearingGroup` pills.
   - **Auto-Balance Assist**: Like Beancount, if the last split's amount is left blank, the form automatically fills its balancing weight with $-\sum \text{other\_weights}$.
   - **Draft Fallback**: If the user only knows one side of the transaction right now (1 split) or splits do not yet sum to zero, they can save it as `Draft`.

---

### 2. Automatic Credit Card Statement Grouping

User outcome:

- Credit card monthly statements are tracked as zero-sum `ClearingGroup`s with **zero manual group selection** during daily bookkeeping.

Rules:

1. When an account has `statement_day` (e.g., `5`) and `due_day` (e.g., `25`), any expense or refund split recorded on that credit card with `tx_date` between Apr 6 and May 5 is **automatically bound** to deterministic group `CG_<account>_2026-05`.
2. **Billing Cycle Shift Override**: If a transaction happened on May 5 but the bank posted it to the June statement, the user can tap the statement pill on that split and switch it to `2026-06 Statement` in one tap.
3. **Statement Repayment**: Tapping **`[Repay]**` on the statement card pre-fills a repayment transaction with `+StatementBalance` on the credit card linked to `CG_<account>_2026-05`. Once saved, the group's balance hits `0` and transitions to `Settled`.

---

### 3. Multi-Loan / Multi-Card Combined Repayment (`InstantBatch`)

User outcome:

- When repaying multiple loan tranches or statements on the same day using one or more bank accounts, all funding and repayment splits are grouped and verified to sum to zero.

Workflow:

1. From the **Dashboard** or **Clearing Hub**, user selects multiple due items (e.g., `Loan A Tranche 1: ¥3,000`, `Loan A Tranche 2: ¥7,000`) and taps **`[Combined Repay (合并还款)]`**.
2. In the sheet, the target liability splits (`+3,000` and `+7,000`) are pre-populated, each already linked to its respective `LoanLifecycle` group AND a newly created `InstantBatch` group (e.g., `"2026-05-20 合并还款"`).
3. User adds funding splits from one or more bank accounts (e.g., `Bank A -6,000` with narration `"招行卡划扣"`, `Bank B -4,000` with narration `"工行卡划扣"`), which are also linked to the `InstantBatch` group.
4. Both the transaction and the `InstantBatch` group balance to `0` (`Settled`), while each loan's `LoanLifecycle` group advances its repayment progress by its exact tranche amount.

---

### 4. Loan & Installment Batch Generator (Amortization Wizard)

User outcome:

- Create a new loan or convert an existing credit card purchase/statement into $N$ future scheduled repayments automatically.

Workflow:

1. Launch **`[New Loan / Installment Plan]`** (standalone or via **`[Convert to Installment (转分期)]`** on an existing credit card split/statement).
2. Configure:
   - `Principal` (e.g., `¥12,000`)
   - `Periods` (e.g., `12 months`)
   - `Method`: Equal Installment / Equal Principal / Flat Fee per Period / Interest-First
   - `Interest Rate / Fee` & `Repayment Day`
   - `Liability Account` & default `Funding Account`
3. Preview the generated amortization schedule table (showing Principal, Interest/Fee, and Remaining Balance for each period).
4. On confirm:
   - Creates a `LoanLifecycle` clearing group (`CG_Loan_<id>`).
   - Records the initial borrowing or statement-transfer split (`-12,000` bound to `CG_Loan_<id>`).
   - Batch-inserts $N$ `Scheduled` transactions for future dates. Each scheduled transaction has:
     - Principal split (`+Principal_i`, bound to `CG_Loan_<id>` and, if on a credit card, that future month's `CreditStatement` group).
     - Interest/Fee split (`+Interest_i` on `Expenses:Finance:Interest`, **not** bound to the principal clearing group so principal clearance stays strictly zero-sum).
     - Funding split (`-(Principal_i + Interest_i)` on `Funding Account`).

---

### 5. Pass-Through Account (转入即消费)

User outcome:

- Transferring money into a petty-cash account (meal card, transit card, family pocket pool) records consumption immediately without tracking small internal spends.

Behavior:

- When transferring `¥500` from `Assets:Bank:CMB` to an account marked `is_pass_through = true` (linked to default expense category `Expenses:Food:MealCard`):
  - The transfer immediately counts as `¥500` of spending in `GlobalDiscretionary` budget and monthly expense reports.
  - The pass-through account's balance is excluded from `Safe-to-Spend` and `Net Worth` (`include_in_net_worth = false`), preventing double-counting.

---

### 6. One-Click Reconciliation (`Auto-Pad`)

User outcome:

- Fix discrepancies between recorded balance and actual bank balance in seconds.

Workflow:

1. On any account, tap **`[Update Actual Balance (核对余额)]`** and enter the real-world balance (e.g., `¥1,200.00` vs. recorded `¥1,350.00`).
2. System calculates the `-¥150.00` drift and creates a balanced adjustment transaction against `Expenses:Adjustment:Unallocated` (with narration `"余额对齐自动平账"`).

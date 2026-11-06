export type AccountCategory = 'Asset' | 'Liability' | 'Income' | 'Expense' | 'Equity';
export type LiquidTier = 'Liquid' | 'SemiLiquid' | 'Illiquid' | 'None';
export type TransactionStatus = 'Draft' | 'Scheduled' | 'Cleared' | 'Void';
export type ClearingGroupType = 'InstantBatch' | 'CreditStatement' | 'LoanLifecycle' | 'Reimbursement';
export type ClearingGroupStatus = 'Active' | 'Settled' | 'Overdrawn';
export type ExchangeRateSource = 'Manual' | 'ImplicitFromSplit' | 'RemoteFeed';
export const AmortizationMethod = {
  EQUAL_INSTALLMENT: 'EqualInstallment',
  EQUAL_PRINCIPAL: 'EqualPrincipal',
  INTEREST_FIRST: 'InterestFirst',
  FLAT_FEE: 'FlatFee',
} as const;
export type AmortizationMethod = (typeof AmortizationMethod)[keyof typeof AmortizationMethod];
export type BudgetScopeType = 'GlobalDiscretionary' | 'AccountSubtree';

export interface FinanceAccount {
  id: string;
  path: string;
  name: string;
  category: AccountCategory;
  currency: string;
  include_in_net_worth: boolean;
  is_pass_through: boolean;
  pass_through_expense_account_id?: string | null;
  liquid_tier: LiquidTier;
  statement_day?: number | null;
  due_day?: number | null;
  is_archived: boolean;
  is_deleted: boolean;
}

export interface FinanceTransaction {
  id: string;
  tx_date: string;
  tx_time?: string | null;
  payee?: string | null;
  summary: string;
  status: TransactionStatus;
  source_plan_id?: string | null;
  period_index?: number | null;
  is_deleted: boolean;
}

export interface FinanceSplit {
  id: string;
  transaction_id: string;
  account_id: string;
  amount_minor: number;
  currency: string;
  weight_amount_minor: number;
  weight_currency: string;
  narration: string;
  sort_order: number;
  is_deleted: boolean;
}

export interface FinanceClearingGroup {
  id: string;
  name: string;
  group_type: ClearingGroupType;
  anchor_currency: string;
  target_account_id?: string | null;
  statement_cycle?: string | null;
  due_date?: string | null;
  status: ClearingGroupStatus;
  settled_at?: string | null;
  is_deleted: boolean;
}

export interface FinanceSplitClearingGroup {
  id: string;
  split_id: string;
  clearing_group_id: string;
  is_deleted: boolean;
}

export interface FinanceExchangeRate {
  id: string;
  base_currency: string;
  quote_currency: string;
  rate_date: string;
  rate_micros: number;
  source: ExchangeRateSource;
  is_deleted: boolean;
}

export interface FinanceAmortizationPlan {
  id: string;
  name: string;
  principal_minor: number;
  currency: string;
  total_periods: number;
  repayment_method: AmortizationMethod;
  annual_rate_bps: number;
  fee_per_period_minor: number;
  start_date: string;
  repayment_day: number;
  liability_account_id: string;
  funding_account_id?: string | null;
  interest_account_id: string;
  lifecycle_clearing_group_id: string;
  is_deleted: boolean;
}

export interface FinanceBudgetRule {
  id: string;
  name: string;
  scope_type: BudgetScopeType;
  account_prefix: string;
  excluded_prefixes_json: string;
  monthly_limit_minor: number;
  currency: string;
  include_in_runway: boolean;
  is_deleted: boolean;
}

export interface TransactionValidationResult {
  valid: boolean;
  reason?: string;
}

export function validateTransactionSplits(
  transaction: FinanceTransaction,
  splits: FinanceSplit[]
): TransactionValidationResult {
  if (transaction.is_deleted) return { valid: true };

  const activeSplits = splits.filter((s) => !s.is_deleted);

  if (transaction.status === 'Draft' || transaction.status === 'Void') {
    return { valid: true };
  }

  if (activeSplits.length < 2) {
    return {
      valid: false,
      reason: `Committed transaction must contain at least 2 active splits (found ${activeSplits.length})`,
    };
  }

  for (const split of activeSplits) {
    const amountSign = Math.sign(split.amount_minor);
    const weightSign = Math.sign(split.weight_amount_minor);
    if (amountSign !== weightSign) {
      return {
        valid: false,
        reason: `Split ${split.id} amount sign (${amountSign}) does not match weight sign (${weightSign})`,
      };
    }
  }

  const byWeightCurrency = new Map<string, number>();
  for (const split of activeSplits) {
    const curr = split.weight_currency.toUpperCase();
    byWeightCurrency.set(curr, (byWeightCurrency.get(curr) ?? 0) + split.weight_amount_minor);
  }

  for (const [curr, sum] of byWeightCurrency.entries()) {
    if (sum !== 0) {
      return {
        valid: false,
        reason: `Splits do not balance to zero for weight currency ${curr} (sum: ${sum} minor units)`,
      };
    }
  }

  return { valid: true };
}

export interface ClearingGroupEvaluation {
  realized_balance: number;
  projected_balance: number;
  derived_status: ClearingGroupStatus;
  cleared_split_count: number;
}

export function evaluateClearingGroup(
  group: FinanceClearingGroup,
  splits: FinanceSplit[],
  transactionsById: Record<string, FinanceTransaction>
): ClearingGroupEvaluation {
  const activeSplits = splits.filter((s) => !s.is_deleted);
  const anchor = group.anchor_currency.toUpperCase();

  const getEffective = (split: FinanceSplit): number => {
    if (split.currency.toUpperCase() === anchor) return split.amount_minor;
    if (split.weight_currency.toUpperCase() === anchor) return split.weight_amount_minor;
    return 0;
  };

  const clearedSplits = activeSplits.filter(
    (s) => transactionsById[s.transaction_id]?.status === 'Cleared'
  );
  const projectedSplits = activeSplits.filter((s) => {
    const st = transactionsById[s.transaction_id]?.status;
    return st === 'Cleared' || st === 'Scheduled';
  });

  const realizedBalance = clearedSplits.reduce((sum, s) => sum + getEffective(s), 0);
  const projectedBalance = projectedSplits.reduce((sum, s) => sum + getEffective(s), 0);
  const clearedCount = clearedSplits.length;

  let derivedStatus: ClearingGroupStatus = 'Active';
  if (clearedCount >= 2 && realizedBalance === 0) {
    derivedStatus = 'Settled';
  } else if (group.group_type === 'LoanLifecycle' && realizedBalance > 0) {
    derivedStatus = 'Overdrawn';
  }

  return {
    realized_balance: realizedBalance,
    projected_balance: projectedBalance,
    derived_status: derivedStatus,
    cleared_split_count: clearedCount,
  };
}

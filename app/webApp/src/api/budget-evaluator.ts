import {
  FinanceBudgetRule,
  FinanceAccount,
  FinanceTransaction,
  FinanceSplit,
} from './finance-types';
import { daysInMonth } from './statement-cycle';

export interface BudgetEvaluationResult {
  ruleId: string;
  ruleName: string;
  currency: string;
  monthlyLimitMinor: number;
  spentMinor: number;
  remainingMinor: number;
  timeElapsedRatio: number;
  spendRatio: number;
  pacingDeltaMinor: number;
  recommendedDailyAllowanceMinor: number;
  isOverBudget: boolean;
  isAheadOfPacing: boolean;
}

export function evaluateBudgetRule(
  rule: FinanceBudgetRule,
  targetYear: number,
  targetMonth: number,
  currentDate: string,
  accounts: Map<string, FinanceAccount>,
  transactions: Map<string, FinanceTransaction>,
  splits: FinanceSplit[]
): BudgetEvaluationResult {
  const numDaysInMonth = daysInMonth(targetYear, targetMonth);
  const parts = currentDate.split('-');
  const currentYear = parseInt(parts[0], 10) || targetYear;
  const currentMonth = parseInt(parts[1], 10) || targetMonth;
  const currentDay = parseInt(parts[2], 10) || 1;

  let clampedDay = 1;
  if (currentYear === targetYear && currentMonth === targetMonth) {
    clampedDay = Math.min(Math.max(1, currentDay), numDaysInMonth);
  } else if (currentYear > targetYear || (currentYear === targetYear && currentMonth > targetMonth)) {
    clampedDay = numDaysInMonth;
  }

  const timeElapsedRatio = clampedDay / numDaysInMonth;
  const remainingDays = Math.max(1, numDaysInMonth - clampedDay + 1);

  let excludedPrefixes: string[] = [];
  try {
    excludedPrefixes = JSON.parse(rule.excluded_prefixes_json || '[]');
  } catch {
    excludedPrefixes = [];
  }

  let totalSpentMinor = 0;

  for (const split of splits) {
    if (split.is_deleted) continue;
    const tx = transactions.get(split.transaction_id);
    if (!tx || tx.is_deleted || tx.status !== 'Cleared') continue;

    const txParts = tx.tx_date.split('-');
    if (txParts.length !== 3) continue;
    const txYear = parseInt(txParts[0], 10);
    const txMonth = parseInt(txParts[1], 10);
    if (txYear !== targetYear || txMonth !== targetMonth) continue;

    const account = accounts.get(split.account_id);
    if (!account) continue;

    let effectivePath: string | null = null;
    if (account.category === 'Expense') {
      effectivePath = account.path;
    } else if (account.is_pass_through && account.pass_through_expense_account_id) {
      effectivePath = accounts.get(account.pass_through_expense_account_id)?.path || null;
    }

    if (!effectivePath) continue;
    if (excludedPrefixes.some((prefix) => effectivePath!.startsWith(prefix))) continue;

    const matched =
      rule.scope_type === 'GlobalDiscretionary' ||
      (rule.scope_type === 'AccountSubtree' && effectivePath.startsWith(rule.account_prefix));

    if (!matched) continue;

    const splitExpenseMinor =
      split.currency.toUpperCase() === rule.currency.toUpperCase()
        ? split.amount_minor
        : split.weight_currency.toUpperCase() === rule.currency.toUpperCase()
        ? split.weight_amount_minor
        : split.amount_minor;

    if (splitExpenseMinor > 0) {
      totalSpentMinor += splitExpenseMinor;
    }
  }

  const remainingMinor = Math.max(0, rule.monthly_limit_minor - totalSpentMinor);
  const spendRatio = rule.monthly_limit_minor > 0 ? totalSpentMinor / rule.monthly_limit_minor : 1.0;
  const expectedSpendToDate = Math.round(rule.monthly_limit_minor * timeElapsedRatio);
  const pacingDeltaMinor = totalSpentMinor - expectedSpendToDate;
  const recommendedDailyAllowanceMinor = Math.floor(remainingMinor / remainingDays);
  const isOverBudget = totalSpentMinor > rule.monthly_limit_minor;
  const isAheadOfPacing = pacingDeltaMinor > 0;

  return {
    ruleId: rule.id,
    ruleName: rule.name,
    currency: rule.currency,
    monthlyLimitMinor: rule.monthly_limit_minor,
    spentMinor: totalSpentMinor,
    remainingMinor,
    timeElapsedRatio,
    spendRatio,
    pacingDeltaMinor,
    recommendedDailyAllowanceMinor,
    isOverBudget,
    isAheadOfPacing,
  };
}

import { describe, it, expect } from 'vitest';
import { evaluateBudgetRule } from '../src/services/budget-evaluator';
import { FinanceBudgetRule, FinanceAccount, FinanceTransaction, FinanceSplit } from '../src/services/finance-types';

describe('BudgetEvaluator TypeScript mirror', () => {
  it('evaluates global discretionary budget with pacing delta and daily allowance', () => {
    const rule: FinanceBudgetRule = {
      id: 'rule-global',
      name: '日常弹性总预算',
      scope_type: 'GlobalDiscretionary',
      account_prefix: 'Expenses:',
      excluded_prefixes_json: JSON.stringify(['Expenses:Finance:Interest', 'Expenses:Housing:Rent']),
      monthly_limit_minor: 600000,
      currency: 'CNY',
      include_in_runway: true,
      is_deleted: false,
    };

    const foodAccount: FinanceAccount = {
      id: 'acc-food',
      path: 'Expenses:Food:Dinner',
      name: '晚餐',
      category: 'Expense',
      currency: 'CNY',
      include_in_net_worth: false,
      is_pass_through: false,
      liquid_tier: 'None',
      is_archived: false,
      is_deleted: false,
    };

    const interestAccount: FinanceAccount = {
      id: 'acc-interest',
      path: 'Expenses:Finance:Interest',
      name: '利息',
      category: 'Expense',
      currency: 'CNY',
      include_in_net_worth: false,
      is_pass_through: false,
      liquid_tier: 'None',
      is_archived: false,
      is_deleted: false,
    };

    const accounts = new Map<string, FinanceAccount>([
      [foodAccount.id, foodAccount],
      [interestAccount.id, interestAccount],
    ]);

    const tx1: FinanceTransaction = {
      id: 'tx-1',
      tx_date: '2026-11-05',
      summary: 'Dinner',
      status: 'Cleared',
      is_deleted: false,
    };

    const tx2: FinanceTransaction = {
      id: 'tx-2',
      tx_date: '2026-11-08',
      summary: 'Interest',
      status: 'Cleared',
      is_deleted: false,
    };

    const transactions = new Map<string, FinanceTransaction>([
      [tx1.id, tx1],
      [tx2.id, tx2],
    ]);

    const splits: FinanceSplit[] = [
      {
        id: 's-1',
        transaction_id: 'tx-1',
        account_id: 'acc-food',
        amount_minor: 250000,
        currency: 'CNY',
        weight_amount_minor: 250000,
        weight_currency: 'CNY',
        narration: '',
        sort_order: 0,
        is_deleted: false,
      },
      {
        id: 's-2',
        transaction_id: 'tx-2',
        account_id: 'acc-interest',
        amount_minor: 50000,
        currency: 'CNY',
        weight_amount_minor: 50000,
        weight_currency: 'CNY',
        narration: '',
        sort_order: 0,
        is_deleted: false,
      },
    ];

    const result = evaluateBudgetRule(
      rule,
      2026,
      11,
      '2026-11-10',
      accounts,
      transactions,
      splits
    );

    expect(result.ruleId).toBe('rule-global');
    expect(result.spentMinor).toBe(250000);
    expect(result.remainingMinor).toBe(350000);
    expect(result.timeElapsedRatio).toBe(10 / 30);
    expect(result.spendRatio).toBe(250000 / 600000);
    expect(result.pacingDeltaMinor).toBe(50000);
    expect(result.isAheadOfPacing).toBe(true);
    expect(result.isOverBudget).toBe(false);
    expect(result.recommendedDailyAllowanceMinor).toBe(Math.floor(350000 / 21));
  });
});

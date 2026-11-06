import { describe, it, expect } from 'vitest';
import { nextDay, projectCashflowRunway } from '../src/services/cashflow-runway';
import {
  FinanceTransaction,
  FinanceSplit,
  FinanceClearingGroup,
} from '../src/services/finance-types';

describe('CashflowRunway', () => {
  it('nextDay handles month ends and leap year correctly', () => {
    expect(nextDay('2026-02-27')).toBe('2026-02-28');
    expect(nextDay('2026-02-28')).toBe('2026-03-01');
    expect(nextDay('2024-02-28')).toBe('2024-02-29');
    expect(nextDay('2024-02-29')).toBe('2024-03-01');
    expect(nextDay('2026-12-30')).toBe('2026-12-31');
    expect(nextDay('2026-12-31')).toBe('2027-01-01');
  });

  it('identifies deficit date when credit statement obligation exceeds cash before payday', () => {
    const initialCash = 500000;
    const liquidAccountIds = new Set(['acc-bank']);

    const stmt: FinanceClearingGroup = {
      id: 'cg-stmt-cc',
      name: '信用卡11月账单',
      group_type: 'CreditStatement',
      anchor_currency: 'CNY',
      due_date: '2026-11-10',
      status: 'Active',
      is_deleted: false,
    };

    const salaryTx: FinanceTransaction = {
      id: 'tx-salary',
      tx_date: '2026-11-15',
      summary: 'Salary',
      status: 'Scheduled',
      is_deleted: false,
    };

    const salarySplit: FinanceSplit = {
      id: 's-sal',
      transaction_id: 'tx-salary',
      account_id: 'acc-bank',
      amount_minor: 1500000,
      currency: 'CNY',
      weight_amount_minor: 1500000,
      weight_currency: 'CNY',
      narration: 'Salary',
      sort_order: 0,
      is_deleted: false,
    };

    const splitsByTx = new Map<string, FinanceSplit[]>([
      ['tx-salary', [salarySplit]],
    ]);
    const statementBalances = new Map<string, number>([
      ['cg-stmt-cc', -600000],
    ]);

    const projection = projectCashflowRunway(
      '2026-11-05',
      30,
      initialCash,
      [salaryTx],
      splitsByTx,
      liquidAccountIds,
      [stmt],
      statementBalances,
      5000
    );

    expect(projection.deficitDate).toBe('2026-11-10');
    expect(projection.daysOfRunway).toBe(5);
    expect(projection.safeToSpendMinor).toBe(0);
    expect(projection.minimumTroughMinor).toBeLessThan(0);
    expect(projection.minimumTroughDate).toBe('2026-11-14');
  });

  it('calculates positive safe-to-spend when cashflow remains solvent', () => {
    const initialCash = 2000000;
    const liquidAccountIds = new Set(['acc-bank']);

    const stmt: FinanceClearingGroup = {
      id: 'cg-stmt-cc',
      name: '信用卡11月账单',
      group_type: 'CreditStatement',
      anchor_currency: 'CNY',
      due_date: '2026-11-10',
      status: 'Active',
      is_deleted: false,
    };

    const loanTx: FinanceTransaction = {
      id: 'tx-loan',
      tx_date: '2026-11-20',
      summary: 'Loan payment',
      status: 'Scheduled',
      is_deleted: false,
    };

    const loanSplit: FinanceSplit = {
      id: 's-loan',
      transaction_id: 'tx-loan',
      account_id: 'acc-bank',
      amount_minor: -200000,
      currency: 'CNY',
      weight_amount_minor: -200000,
      weight_currency: 'CNY',
      narration: 'Installment payment',
      sort_order: 0,
      is_deleted: false,
    };

    const splitsByTx = new Map<string, FinanceSplit[]>([
      ['tx-loan', [loanSplit]],
    ]);
    const statementBalances = new Map<string, number>([
      ['cg-stmt-cc', -500000],
    ]);

    const projection = projectCashflowRunway(
      '2026-11-01',
      30,
      initialCash,
      [loanTx],
      splitsByTx,
      liquidAccountIds,
      [stmt],
      statementBalances,
      10000
    );

    expect(projection.deficitDate).toBeNull();
    expect(projection.daysOfRunway).toBe(30);
    expect(projection.safeToSpendMinor).toBe(1000000);
    expect(projection.minimumTroughMinor).toBe(1000000);
  });
});

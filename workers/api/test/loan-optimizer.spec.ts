import { describe, it, expect } from 'vitest';
import { simulatePrepayment, LoanInput } from '../src/services/loan-optimizer';

describe('LoanPrepaymentOptimizer', () => {
  it('avalanche saves >= interest than snowball and prioritizes highest APR', () => {
    const loanHighApr: LoanInput = {
      id: 'loan-credit-card',
      name: '信用卡账单分期',
      principalMinor: 1000000,
      annualRateBps: 1500,
      minimumMonthlyPaymentMinor: 50000,
    };
    const loanLowAprSmallBalance: LoanInput = {
      id: 'loan-device',
      name: '数码分期',
      principalMinor: 300000,
      annualRateBps: 450,
      minimumMonthlyPaymentMinor: 25000,
    };

    const comparison = simulatePrepayment(
      [loanHighApr, loanLowAprSmallBalance],
      30000
    );

    // Baseline:
    expect(comparison.baseline.totalMonths).toBeGreaterThan(0);
    expect(comparison.baseline.totalInterestMinor).toBeGreaterThan(0);
    expect(comparison.baseline.interestSavedMinor).toBe(0);
    expect(comparison.baseline.monthsShortened).toBe(0);

    // Invariant: Avalanche saves at least as much or more interest than Snowball
    expect(comparison.avalanche.interestSavedMinor).toBeGreaterThanOrEqual(
      comparison.snowball.interestSavedMinor
    );
    expect(comparison.avalanche.totalInterestMinor).toBeLessThanOrEqual(
      comparison.snowball.totalInterestMinor
    );
    expect(comparison.avalanche.monthsShortened).toBeGreaterThan(0);

    // Snowball: targets smallest balance (loan-device) first
    expect(comparison.snowball.payoffOrder[0]).toBe('loan-device');
    expect(comparison.snowball.monthsShortened).toBeGreaterThan(0);
    expect(comparison.snowball.interestSavedMinor).toBeGreaterThan(0);
  });

  it('handles empty or zero balance loans gracefully', () => {
    const emptyResult = simulatePrepayment([], 50000);
    expect(emptyResult.baseline.totalInterestMinor).toBe(0);
    expect(emptyResult.baseline.totalMonths).toBe(0);
    expect(emptyResult.avalanche.interestSavedMinor).toBe(0);
    expect(emptyResult.snowball.interestSavedMinor).toBe(0);
  });
});

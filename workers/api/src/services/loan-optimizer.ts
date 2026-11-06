export interface LoanInput {
  id: string;
  name: string;
  principalMinor: number;
  annualRateBps: number;
  minimumMonthlyPaymentMinor: number;
}

export interface StrategyResult {
  totalInterestMinor: number;
  totalMonths: number;
  interestSavedMinor: number;
  monthsShortened: number;
  payoffOrder: string[];
}

export interface PrepaymentComparison {
  baseline: StrategyResult;
  avalanche: StrategyResult;
  snowball: StrategyResult;
}

type StrategyType = 'NONE' | 'AVALANCHE' | 'SNOWBALL';

interface LoanState {
  input: LoanInput;
  remainingMinor: number;
  monthlyRate: number;
}

export function simulatePrepayment(
  loans: LoanInput[],
  extraMonthlyPaymentMinor = 0
): PrepaymentComparison {
  const baseline = simulateStrategy(loans, 0, 'NONE');
  const avalanche = simulateStrategy(loans, extraMonthlyPaymentMinor, 'AVALANCHE', baseline);
  const snowball = simulateStrategy(loans, extraMonthlyPaymentMinor, 'SNOWBALL', baseline);

  return {
    baseline,
    avalanche,
    snowball,
  };
}

function simulateStrategy(
  loans: LoanInput[],
  extraMonthlyPaymentMinor: number,
  strategy: StrategyType,
  baseline?: StrategyResult
): StrategyResult {
  if (loans.length === 0) {
    return {
      totalInterestMinor: 0,
      totalMonths: 0,
      interestSavedMinor: 0,
      monthsShortened: 0,
      payoffOrder: [],
    };
  }

  const states: LoanState[] = loans
    .filter((l) => l.principalMinor > 0)
    .map((l) => ({
      input: l,
      remainingMinor: l.principalMinor,
      monthlyRate: l.annualRateBps / 10000.0 / 12.0,
    }));

  let totalInterest = 0;
  let month = 0;
  const payoffOrder: string[] = [];
  const maxMonths = 360;

  const originalTotalCommitment =
    loans.reduce((acc, l) => acc + l.minimumMonthlyPaymentMinor, 0) + extraMonthlyPaymentMinor;

  while (states.some((s) => s.remainingMinor > 0) && month < maxMonths) {
    month++;

    // 1. Accrue monthly interest
    for (const state of states) {
      if (state.remainingMinor > 0) {
        const interest = Math.round(state.remainingMinor * state.monthlyRate);
        state.remainingMinor += interest;
        totalInterest += interest;
      }
    }

    // 2. Pay minimums
    let budgetRemaining = originalTotalCommitment;
    for (const state of states) {
      if (state.remainingMinor > 0) {
        const minPay = Math.min(state.input.minimumMonthlyPaymentMinor, state.remainingMinor);
        state.remainingMinor -= minPay;
        budgetRemaining -= minPay;
        if (state.remainingMinor === 0 && !payoffOrder.includes(state.input.id)) {
          payoffOrder.push(state.input.id);
        }
      }
    }

    // 3. Allocate freed-up snowball & extra prepayment
    if (budgetRemaining > 0 && strategy !== 'NONE') {
      const activeStates = states.filter((s) => s.remainingMinor > 0);
      const targetOrder = [...activeStates].sort((a, b) => {
        if (strategy === 'AVALANCHE') {
          // Highest APR first, then smallest balance
          if (b.input.annualRateBps !== a.input.annualRateBps) {
            return b.input.annualRateBps - a.input.annualRateBps;
          }
          return a.remainingMinor - b.remainingMinor;
        } else {
          // Smallest balance first, then highest APR
          if (a.remainingMinor !== b.remainingMinor) {
            return a.remainingMinor - b.remainingMinor;
          }
          return b.input.annualRateBps - a.input.annualRateBps;
        }
      });

      for (const target of targetOrder) {
        if (budgetRemaining <= 0) break;
        const pay = Math.min(budgetRemaining, target.remainingMinor);
        target.remainingMinor -= pay;
        budgetRemaining -= pay;
        if (target.remainingMinor === 0 && !payoffOrder.includes(target.input.id)) {
          payoffOrder.push(target.input.id);
        }
      }
    }
  }

  const baseInterest = baseline ? baseline.totalInterestMinor : totalInterest;
  const baseMonths = baseline ? baseline.totalMonths : month;

  return {
    totalInterestMinor: totalInterest,
    totalMonths: month,
    interestSavedMinor: Math.max(0, baseInterest - totalInterest),
    monthsShortened: Math.max(0, baseMonths - month),
    payoffOrder,
  };
}

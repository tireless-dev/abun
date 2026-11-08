import {
  FinanceTransaction,
  FinanceSplit,
  FinanceClearingGroup,
} from './finance-types';
import { daysInMonth } from './statement-cycle';

export interface DailyCashflowPoint {
  date: string;
  dayIndex: number;
  projectedLiquidCashMinor: number;
  scheduledInflowsMinor: number;
  scheduledOutflowsMinor: number;
  statementObligationsMinor: number;
  dailyDiscretionaryBurnMinor: number;
  isDeficit: boolean;
}

export interface CashflowRunwayProjection {
  initialLiquidCashMinor: number;
  safeToSpendMinor: number;
  deficitDate: string | null;
  daysOfRunway: number;
  minimumTroughMinor: number;
  minimumTroughDate: string;
  dailyPoints: DailyCashflowPoint[];
}

export function nextDay(dateStr: string): string {
  const parts = dateStr.split('-');
  let year = parseInt(parts[0], 10);
  let month = parseInt(parts[1], 10);
  let day = parseInt(parts[2], 10);

  const numDays = daysInMonth(year, month);
  if (day < numDays) {
    day += 1;
  } else {
    day = 1;
    if (month < 12) {
      month += 1;
    } else {
      month = 1;
      year += 1;
    }
  }
  return `${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}`;
}

export function projectCashflowRunway(
  currentDate: string,
  horizonDays = 90,
  currentLiquidCashMinor: number,
  scheduledTransactions: FinanceTransaction[],
  splitsByTx: Map<string, FinanceSplit[]>,
  liquidAccountIds: Set<string>,
  unsettledStatements: FinanceClearingGroup[],
  statementBalancesByGroupId: Map<string, number>,
  dailyLivingBurnMinor: number
): CashflowRunwayProjection {
  if (horizonDays <= 0) throw new Error('horizonDays must be > 0');

  const scheduledByDate = new Map<string, FinanceTransaction[]>();
  for (const tx of scheduledTransactions) {
    if (!tx.is_deleted && tx.status === 'Scheduled') {
      const list = scheduledByDate.get(tx.tx_date) ?? [];
      list.push(tx);
      scheduledByDate.set(tx.tx_date, list);
    }
  }

  const statementsByDueDate = new Map<string, FinanceClearingGroup[]>();
  for (const stmt of unsettledStatements) {
    if (!stmt.is_deleted && stmt.status === 'Active' && stmt.due_date) {
      const list = statementsByDueDate.get(stmt.due_date) ?? [];
      list.push(stmt);
      statementsByDueDate.set(stmt.due_date, list);
    }
  }

  const points: DailyCashflowPoint[] = [];
  let runningCash = currentLiquidCashMinor;
  let deficitDate: string | null = null;
  let minTroughMinor = currentLiquidCashMinor;
  let minTroughDate = currentDate;

  let dateCursor = currentDate;

  for (let d = 0; d <= horizonDays; d++) {
    const scheduledTxs = scheduledByDate.get(dateCursor) ?? [];
    let inflows = 0;
    let outflows = 0;

    for (const tx of scheduledTxs) {
      const splits = splitsByTx.get(tx.id) ?? [];
      const netLiquidChange = splits
        .filter((s) => liquidAccountIds.has(s.account_id))
        .reduce((sum, s) => sum + s.amount_minor, 0);

      if (netLiquidChange > 0) {
        inflows += netLiquidChange;
      } else if (netLiquidChange < 0) {
        outflows += -netLiquidChange;
      }
    }

    const dueStatements = statementsByDueDate.get(dateCursor) ?? [];
    let statementObligations = 0;
    for (const stmt of dueStatements) {
      const balance = statementBalancesByGroupId.get(stmt.id) ?? 0;
      if (balance < 0) {
        statementObligations += -balance;
      }
    }

    const burn = d > 0 ? dailyLivingBurnMinor : 0;

    runningCash = runningCash + inflows - outflows - statementObligations - burn;

    const isDeficit = runningCash < 0;
    if (isDeficit && deficitDate === null) {
      deficitDate = dateCursor;
    }

    if (runningCash < minTroughMinor) {
      minTroughMinor = runningCash;
      minTroughDate = dateCursor;
    }

    points.push({
      date: dateCursor,
      dayIndex: d,
      projectedLiquidCashMinor: runningCash,
      scheduledInflowsMinor: inflows,
      scheduledOutflowsMinor: outflows,
      statementObligationsMinor: statementObligations,
      dailyDiscretionaryBurnMinor: burn,
      isDeficit,
    });

    dateCursor = nextDay(dateCursor);
  }

  const daysOfRunway =
    deficitDate !== null
      ? Math.max(0, points.findIndex((p) => p.date === deficitDate))
      : horizonDays;

  const safeToSpend = Math.max(0, minTroughMinor);

  return {
    initialLiquidCashMinor: currentLiquidCashMinor,
    safeToSpendMinor: safeToSpend,
    deficitDate,
    daysOfRunway,
    minimumTroughMinor: minTroughMinor,
    minimumTroughDate: minTroughDate,
    dailyPoints: points,
  };
}

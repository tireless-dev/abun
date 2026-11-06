export * from "./finance-types";

export interface StatementCycleInfo {
  cycleTag: string;
  statementClosingDate: string;
  dueDate: string;
  deterministicGroupId: string;
  defaultGroupName: string;
}

export function isLeapYear(year: number): boolean {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
}

export function daysInMonth(year: number, month: number): number {
  switch (month) {
    case 2:
      return isLeapYear(year) ? 29 : 28;
    case 4:
    case 6:
    case 9:
    case 11:
      return 30;
    default:
      return 31;
  }
}

export function calculateStatementCycle(
  accountId: string,
  accountName: string,
  txDate: string,
  statementDay: number,
  dueDay: number,
): StatementCycleInfo {
  const parts = txDate.split("-");
  if (parts.length !== 3) {
    throw new Error(`Invalid txDate format: ${txDate}, expected YYYY-MM-DD`);
  }
  const year = parseInt(parts[0], 10);
  const month = parseInt(parts[1], 10);
  const day = parseInt(parts[2], 10);

  const effectiveStmtDayThisMonth = Math.min(statementDay, daysInMonth(year, month));

  let cycleYear = year;
  let cycleMonth = month;
  if (day > effectiveStmtDayThisMonth) {
    if (month === 12) {
      cycleYear = year + 1;
      cycleMonth = 1;
    } else {
      cycleMonth = month + 1;
    }
  }

  const closingDay = Math.min(statementDay, daysInMonth(cycleYear, cycleMonth));
  const closingMonthStr = String(cycleMonth).padStart(2, "0");
  const closingDayStr = String(closingDay).padStart(2, "0");
  const cycleTag = `${cycleYear}-${closingMonthStr}`;
  const statementClosingDate = `${cycleYear}-${closingMonthStr}-${closingDayStr}`;

  let dueYear = cycleYear;
  let dueMonth = cycleMonth;
  if (dueDay < statementDay) {
    if (cycleMonth === 12) {
      dueYear = cycleYear + 1;
      dueMonth = 1;
    } else {
      dueMonth = cycleMonth + 1;
    }
  }

  const effectiveDueDay = Math.min(dueDay, daysInMonth(dueYear, dueMonth));
  const dueMonthStr = String(dueMonth).padStart(2, "0");
  const dueDayStr = String(effectiveDueDay).padStart(2, "0");
  const dueDate = `${dueYear}-${dueMonthStr}-${dueDayStr}`;

  const deterministicGroupId = `cg-stmt:${accountId}:${cycleTag}`;
  const defaultGroupName = `${accountName} ${cycleTag} 账单`;

  return {
    cycleTag,
    statementClosingDate,
    dueDate,
    deterministicGroupId,
    defaultGroupName,
  };
}

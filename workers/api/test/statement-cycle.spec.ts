import { describe, expect, it } from "vitest";
import { calculateStatementCycle } from "../src/services/statement-cycle";

describe("StatementCycleCalculator TypeScript mirror", () => {
  it("determines cycle before or on statement day", () => {
    const cycle = calculateStatementCycle("acc-cc-1", "CMB Credit Card", "2026-05-04", 5, 25);
    expect(cycle.cycleTag).toBe("2026-05");
    expect(cycle.statementClosingDate).toBe("2026-05-05");
    expect(cycle.dueDate).toBe("2026-05-25");
    expect(cycle.deterministicGroupId).toBe("cg-stmt:acc-cc-1:2026-05");
  });

  it("rolls to next month cycle after statement day", () => {
    const cycle = calculateStatementCycle("acc-cc-1", "CMB Credit Card", "2026-05-06", 5, 25);
    expect(cycle.cycleTag).toBe("2026-06");
    expect(cycle.statementClosingDate).toBe("2026-06-05");
    expect(cycle.dueDate).toBe("2026-06-25");
  });

  it("handles December to January year boundary rollover", () => {
    const cycle = calculateStatementCycle("acc-cc-1", "CMB Credit Card", "2026-12-10", 5, 25);
    expect(cycle.cycleTag).toBe("2027-01");
    expect(cycle.statementClosingDate).toBe("2027-01-05");
    expect(cycle.dueDate).toBe("2027-01-25");
  });

  it("rolls due date to next month when dueDay < statementDay", () => {
    const cycle = calculateStatementCycle("acc-cc-1", "CMB Credit Card", "2026-05-15", 20, 10);
    expect(cycle.cycleTag).toBe("2026-05");
    expect(cycle.statementClosingDate).toBe("2026-05-20");
    expect(cycle.dueDate).toBe("2026-06-10");
  });
});

import { describe, expect, it } from "vitest";
import { AmortizationMethod } from "../src/services/finance-types";
import { generateAmortizationSchedule } from "../src/services/amortization";

describe("AmortizationGenerator TypeScript mirror", () => {
  it("generates equal principal schedule and sums to total principal", () => {
    const principal = 1200000;
    const periods = 12;
    const schedule = generateAmortizationSchedule(
      principal,
      periods,
      AmortizationMethod.EQUAL_PRINCIPAL,
      600,
      0,
      15,
      "2026-06-15",
    );

    expect(schedule.length).toBe(periods);
    const sumPrincipal = schedule.reduce((sum, item) => sum + item.principalMinor, 0);
    expect(sumPrincipal).toBe(principal);
    expect(schedule[schedule.length - 1].remainingPrincipalMinor).toBe(0);
  });

  it("generates equal installment schedule and reconciles final principal", () => {
    const principal = 1000000;
    const periods = 6;
    const schedule = generateAmortizationSchedule(
      principal,
      periods,
      AmortizationMethod.EQUAL_INSTALLMENT,
      480,
      0,
      10,
      "2026-06-10",
    );

    expect(schedule.length).toBe(periods);
    const sumPrincipal = schedule.reduce((sum, item) => sum + item.principalMinor, 0);
    expect(sumPrincipal).toBe(principal);
    expect(schedule[schedule.length - 1].remainingPrincipalMinor).toBe(0);
  });
});

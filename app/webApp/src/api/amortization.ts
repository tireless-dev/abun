import { AmortizationMethod } from "./finance-types";
import { daysInMonth } from "./statement-cycle";

export interface AmortizationScheduleItem {
  periodIndex: number;
  dueDate: string;
  principalMinor: number;
  interestMinor: number;
  feeMinor: number;
  totalPaymentMinor: number;
  remainingPrincipalMinor: number;
}

export function generateAmortizationSchedule(
  principalMinor: number,
  totalPeriods: number,
  method: AmortizationMethod,
  annualRateBps: number,
  feePerPeriodMinor = 0,
  repaymentDay: number,
  firstRepaymentDate: string,
): AmortizationScheduleItem[] {
  if (principalMinor <= 0) throw new Error("Principal must be greater than 0");
  if (totalPeriods <= 0) throw new Error("Total periods must be greater than 0");

  const parts = firstRepaymentDate.split("-");
  if (parts.length !== 3) throw new Error(`Invalid firstRepaymentDate: ${firstRepaymentDate}`);
  const startYear = parseInt(parts[0], 10);
  const startMonth = parseInt(parts[1], 10);

  const monthlyRate = annualRateBps / 10000.0 / 12.0;
  let remainingPrincipal = principalMinor;
  const items: AmortizationScheduleItem[] = [];

  let pmtEqualInstallment = 0;
  if (method === AmortizationMethod.EQUAL_INSTALLMENT && monthlyRate > 0) {
    const factor = Math.pow(1 + monthlyRate, totalPeriods);
    pmtEqualInstallment = Math.round((principalMinor * monthlyRate * factor) / (factor - 1.0));
  }

  for (let period = 1; period <= totalPeriods; period++) {
    const totalMonths = startMonth - 1 + (period - 1);
    const currentYear = startYear + Math.floor(totalMonths / 12);
    const currentMonth = (totalMonths % 12) + 1;
    const maxDay = daysInMonth(currentYear, currentMonth);
    const day = period === 1 ? Math.min(parseInt(parts[2], 10), maxDay) : Math.min(repaymentDay, maxDay);
    const dueDate = `${currentYear}-${String(currentMonth).padStart(2, "0")}-${String(day).padStart(2, "0")}`;

    const isLastPeriod = period === totalPeriods;
    let periodPrincipal = 0;
    let periodInterest = 0;

    switch (method) {
      case AmortizationMethod.EQUAL_PRINCIPAL: {
        periodPrincipal = isLastPeriod ? remainingPrincipal : Math.floor(principalMinor / totalPeriods);
        periodInterest = Math.round(remainingPrincipal * monthlyRate);
        break;
      }
      case AmortizationMethod.EQUAL_INSTALLMENT: {
        if (monthlyRate === 0) {
          periodPrincipal = isLastPeriod ? remainingPrincipal : Math.floor(principalMinor / totalPeriods);
          periodInterest = 0;
        } else {
          periodInterest = Math.round(remainingPrincipal * monthlyRate);
          periodPrincipal = isLastPeriod ? remainingPrincipal : Math.min(pmtEqualInstallment - periodInterest, remainingPrincipal);
        }
        break;
      }
      case AmortizationMethod.FLAT_FEE: {
        periodPrincipal = isLastPeriod ? remainingPrincipal : Math.floor(principalMinor / totalPeriods);
        periodInterest = 0;
        break;
      }
      case AmortizationMethod.INTEREST_FIRST: {
        periodPrincipal = isLastPeriod ? remainingPrincipal : 0;
        periodInterest = Math.round(principalMinor * monthlyRate);
        break;
      }
    }

    remainingPrincipal -= periodPrincipal;
    const totalPayment = periodPrincipal + periodInterest + feePerPeriodMinor;

    items.push({
      periodIndex: period,
      dueDate,
      principalMinor: periodPrincipal,
      interestMinor: periodInterest,
      feeMinor: feePerPeriodMinor,
      totalPaymentMinor: totalPayment,
      remainingPrincipalMinor: Math.max(0, remainingPrincipal),
    });
  }

  return items;
}

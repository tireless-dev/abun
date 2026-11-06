import { describe, expect, it } from "vitest";
import {
  evaluateClearingGroup,
  type FinanceClearingGroup,
  type FinanceSplit,
  type FinanceTransaction,
  validateTransactionSplits,
} from "../src/services/finance-types";

describe("finance contracts & validation in TypeScript", () => {
  it("validates standard balanced 2-split transaction", () => {
    const tx: FinanceTransaction = {
      id: "tx-1",
      tx_date: "2026-11-05",
      summary: "Grocery shopping",
      status: "Cleared",
      is_deleted: false,
    };
    const splits: FinanceSplit[] = [
      {
        id: "s-1",
        transaction_id: "tx-1",
        account_id: "acc-bank",
        amount_minor: -10050,
        currency: "CNY",
        weight_amount_minor: -10050,
        weight_currency: "CNY",
        narration: "Bank debit",
        sort_order: 0,
        is_deleted: false,
      },
      {
        id: "s-2",
        transaction_id: "tx-1",
        account_id: "acc-food",
        amount_minor: 10050,
        currency: "CNY",
        weight_amount_minor: 10050,
        weight_currency: "CNY",
        narration: "Food items",
        sort_order: 1,
        is_deleted: false,
      },
    ];

    const result = validateTransactionSplits(tx, splits);
    expect(result.valid).toBe(true);
  });

  it("fails unbalanced committed transactions", () => {
    const tx: FinanceTransaction = {
      id: "tx-2",
      tx_date: "2026-11-05",
      summary: "Unbalanced",
      status: "Cleared",
      is_deleted: false,
    };
    const splits: FinanceSplit[] = [
      {
        id: "s-1",
        transaction_id: "tx-2",
        account_id: "acc-bank",
        amount_minor: -10000,
        currency: "CNY",
        weight_amount_minor: -10000,
        weight_currency: "CNY",
        narration: "",
        sort_order: 0,
        is_deleted: false,
      },
      {
        id: "s-2",
        transaction_id: "tx-2",
        account_id: "acc-food",
        amount_minor: 9500,
        currency: "CNY",
        weight_amount_minor: 9500,
        weight_currency: "CNY",
        narration: "",
        sort_order: 1,
        is_deleted: false,
      },
    ];

    const result = validateTransactionSplits(tx, splits);
    expect(result.valid).toBe(false);
    expect(result.reason).toContain("balance to zero");
  });

  it("allows draft transactions with 0 or 1 split", () => {
    const draftTx: FinanceTransaction = {
      id: "tx-draft",
      tx_date: "2026-11-05",
      summary: "Draft entry",
      status: "Draft",
      is_deleted: false,
    };

    expect(validateTransactionSplits(draftTx, []).valid).toBe(true);

    const singleSplit: FinanceSplit[] = [
      {
        id: "s-1",
        transaction_id: "tx-draft",
        account_id: "acc-expense",
        amount_minor: 5000,
        currency: "CNY",
        weight_amount_minor: 5000,
        weight_currency: "CNY",
        narration: "Single split draft",
        sort_order: 0,
        is_deleted: false,
      },
    ];
    expect(validateTransactionSplits(draftTx, singleSplit).valid).toBe(true);
  });

  it("evaluates clearing group zero-sum settlement", () => {
    const group: FinanceClearingGroup = {
      id: "cg-loan",
      name: "装修贷款本金清偿",
      group_type: "LoanLifecycle",
      anchor_currency: "CNY",
      status: "Active",
      is_deleted: false,
    };

    const txBorrow: FinanceTransaction = { id: "tx-b", tx_date: "2026-01-01", summary: "", status: "Cleared", is_deleted: false };
    const txRepay: FinanceTransaction = { id: "tx-r", tx_date: "2026-02-01", summary: "", status: "Cleared", is_deleted: false };
    const txMap: Record<string, FinanceTransaction> = {
      [txBorrow.id]: txBorrow,
      [txRepay.id]: txRepay,
    };

    const splits: FinanceSplit[] = [
      {
        id: "s-borrow",
        transaction_id: txBorrow.id,
        account_id: "acc-loan",
        amount_minor: -1000000,
        currency: "CNY",
        weight_amount_minor: -1000000,
        weight_currency: "CNY",
        narration: "",
        sort_order: 0,
        is_deleted: false,
      },
      {
        id: "s-repay",
        transaction_id: txRepay.id,
        account_id: "acc-loan",
        amount_minor: 1000000,
        currency: "CNY",
        weight_amount_minor: 1000000,
        weight_currency: "CNY",
        narration: "",
        sort_order: 0,
        is_deleted: false,
      },
    ];

    const evalResult = evaluateClearingGroup(group, splits, txMap);
    expect(evalResult.realized_balance).toBe(0);
    expect(evalResult.derived_status).toBe("Settled");
    expect(evalResult.cleared_split_count).toBe(2);
  });
});

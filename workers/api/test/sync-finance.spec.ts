import { describe, expect, it } from "vitest";
import worker from "../src/index";
import { createPassThroughAuthEnv } from "./helpers/auth";

type FetchHandler = (
  request: Request,
  env: unknown,
  ctx: ExecutionContext,
) => Promise<Response>;

function getFetchHandler(): FetchHandler {
  return worker.fetch.bind(worker) as FetchHandler;
}

describe("finance sync endpoints", () => {
  it("syncs finance accounts with HLC resolution and pagination", async () => {
    const fetchHandler = getFetchHandler();
    const env = createPassThroughAuthEnv();

    // 1. Push account for user-1
    const pushResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-accounts", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "acc-cmbc",
              path: "Liabilities:CreditCard:CMBC",
              name: "招商银行信用卡",
              category: "Liability",
              currency: "CNY",
              include_in_net_worth: true,
              statement_day: 5,
              due_day: 25,
              hlc_map: { core: "1000-0001-devA" },
              dirty_fields: ["core"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(pushResponse.status).toBe(200);
    const pushJson = await pushResponse.json();
    expect(pushJson).toEqual({
      items: [
        expect.objectContaining({
          id: "acc-cmbc",
          name: "招商银行信用卡",
          accepted_fields: ["core"],
          rejected_fields: [],
        }),
      ],
    });

    // 2. Pull account for user-1
    const pullResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-accounts?cursor=0&limit=10", {
        method: "GET",
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(pullResponse.status).toBe(200);
    const pullJson = (await pullResponse.json()) as any;
    expect(pullJson.items).toHaveLength(1);
    expect(pullJson.items[0]).toMatchObject({
      id: "acc-cmbc",
      path: "Liabilities:CreditCard:CMBC",
      category: "Liability",
    });

    // 3. User isolation: user-2 should see empty list
    const user2PullResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-accounts?cursor=0&limit=10", {
        method: "GET",
        headers: { authorization: "Bearer user-2" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(((await user2PullResponse.json()) as any).items).toHaveLength(0);

    // 4. HLC conflict resolution: older HLC is rejected
    const olderHlcPushResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-accounts", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "acc-cmbc",
              path: "Liabilities:CreditCard:CMBC",
              name: "老旧名字",
              category: "Liability",
              hlc_map: { core: "0500-0001-devB" }, // Older than 1000-0001-devA
              dirty_fields: ["core"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );
    const olderJson = (await olderHlcPushResponse.json()) as any;
    expect(olderJson.items[0].rejected_fields).toContain("core");
    expect(olderJson.items[0].name).toBe("招商银行信用卡"); // Retains existing

    // 5. Newer HLC is accepted
    const newerHlcPushResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-accounts", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "acc-cmbc",
              path: "Liabilities:CreditCard:CMBC",
              name: "招商银行信用卡 (已更新)",
              category: "Liability",
              hlc_map: { core: "2000-0001-devB" }, // Newer
              dirty_fields: ["core"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );
    const newerJson = (await newerHlcPushResponse.json()) as any;
    expect(newerJson.items[0].accepted_fields).toContain("core");
    expect(newerJson.items[0].name).toBe("招商银行信用卡 (已更新)");
  });

  it("syncs exchange rates, clearing groups, transactions, splits, and links", async () => {
    const fetchHandler = getFetchHandler();
    const env = createPassThroughAuthEnv();

    // 1. Push exchange rate
    const fxResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-exchange-rates", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "fx-usd-cny-2026-05-24",
              base_currency: "USD",
              quote_currency: "CNY",
              rate_date: "2026-05-24",
              rate_micros: 7200000,
              source: "ECB",
              hlc_map: { core: "1000-0001-devA" },
              dirty_fields: ["core"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(fxResponse.status).toBe(200);

    // 2. Push clearing group
    const cgResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-clearing-groups", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "cg-stmt:acc-cmbc:2026-05",
              name: "招商信用卡 2026-05 账单",
              group_type: "CreditCardCycle",
              anchor_currency: "CNY",
              statement_cycle: "2026-05",
              due_date: "2026-05-25",
              status: "Active",
              hlc_map: { core: "1000-0001-devA" },
              dirty_fields: ["core"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(cgResponse.status).toBe(200);

    // 3. Push transaction
    const txResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-transactions", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "tx-dinner-1",
              tx_date: "2026-05-20",
              payee: "海底捞",
              summary: "团队聚餐",
              status: "Cleared",
              hlc_map: { header: "1000-0001-devA" },
              dirty_fields: ["header"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(txResponse.status).toBe(200);

    // 4. Push splits
    const splitResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-splits", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "split-1",
              transaction_id: "tx-dinner-1",
              account_id: "Expenses:Food:Dinner",
              amount_minor: 45000,
              currency: "CNY",
              weight_amount_minor: 45000,
              weight_currency: "CNY",
              narration: "聚餐消费",
              sort_order: 0,
              hlc_map: { content: "1000-0001-devA" },
              dirty_fields: ["content"],
            },
            {
              id: "split-2",
              transaction_id: "tx-dinner-1",
              account_id: "Liabilities:CreditCard:CMBC",
              amount_minor: -45000,
              currency: "CNY",
              weight_amount_minor: -45000,
              weight_currency: "CNY",
              narration: "招行信用卡支付",
              sort_order: 1,
              hlc_map: { content: "1000-0001-devA" },
              dirty_fields: ["content"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(splitResponse.status).toBe(200);

    // 5. Push split-clearing-group link
    const linkResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-split-clearing-groups", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "scg-1",
              split_id: "split-2",
              clearing_group_id: "cg-stmt:acc-cmbc:2026-05",
              hlc_map: { link: "1000-0001-devA" },
              dirty_fields: ["link"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(linkResponse.status).toBe(200);

    // 6. Pull all to verify
    const pullSplits = await fetchHandler(
      new Request("http://example.com/api/sync/finance-splits?cursor=0&limit=10", {
        method: "GET",
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(pullSplits.status).toBe(200);
    const splitsJson = (await pullSplits.json()) as any;
    expect(splitsJson.items).toHaveLength(2);
    expect(splitsJson.items[0].amount_minor).toBe(45000);
    expect(splitsJson.items[1].amount_minor).toBe(-45000);
  });

  it("syncs amortization plans and budget rules", async () => {
    const fetchHandler = getFetchHandler();
    const env = createPassThroughAuthEnv();

    // 1. Push amortization plan
    const planResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-amortization-plans", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "plan-mortgage",
              name: "住房公积金贷款",
              principal_minor: 120000000,
              currency: "CNY",
              total_periods: 240,
              repayment_method: "EqualPrincipalAndInterest",
              annual_rate_bps: 285,
              start_date: "2026-01-20",
              repayment_day: 20,
              liability_account_id: "Liabilities:Mortgage",
              funding_account_id: "Assets:Checking:ICBC",
              interest_account_id: "Expenses:Finance:Interest",
              lifecycle_clearing_group_id: "cg-mortgage-lifecycle",
              hlc_map: { core: "1000-0001-devA" },
              dirty_fields: ["core"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(planResponse.status).toBe(200);
    const planJson = (await planResponse.json()) as any;
    expect(planJson.items[0].principal_minor).toBe(120000000);

    // 2. Push budget rule
    const ruleResponse = await fetchHandler(
      new Request("http://example.com/api/sync/finance-budget-rules", {
        method: "POST",
        headers: {
          authorization: "Bearer user-1",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          items: [
            {
              id: "rule-food",
              name: "餐饮与食品",
              scope_type: "Prefix",
              account_prefix: "Expenses:Food",
              excluded_prefixes_json: "[]",
              monthly_limit_minor: 300000,
              currency: "CNY",
              include_in_runway: true,
              hlc_map: { core: "1000-0001-devA" },
              dirty_fields: ["core"],
            },
          ],
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(ruleResponse.status).toBe(200);
    const ruleJson = (await ruleResponse.json()) as any;
    expect(ruleJson.items[0].monthly_limit_minor).toBe(300000);

    // 3. Pull budget rules
    const pullRules = await fetchHandler(
      new Request("http://example.com/api/sync/finance-budget-rules?cursor=0&limit=10", {
        method: "GET",
        headers: { authorization: "Bearer user-1" },
      }) as never,
      env,
      {} as ExecutionContext,
    );
    expect(pullRules.status).toBe(200);
    const rulesList = (await pullRules.json()) as any;
    expect(rulesList.items).toHaveLength(1);
    expect(rulesList.items[0].account_prefix).toBe("Expenses:Food");
  });
});

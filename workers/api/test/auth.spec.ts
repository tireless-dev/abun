import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("api worker otp auth", () => {
  it("matches the shared test account otp contract", async () => {
    const handler = worker as ExportedHandler;
    const fetchHandler = handler.fetch as NonNullable<ExportedHandler["fetch"]>;
    const env = {} as never;

    const otpRequestResponse = await fetchHandler(
      new Request("http://example.com/api/auth/otp/request", {
        method: "POST",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({
          email: "  ABUN@TIRELESS.DEV  ",
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(otpRequestResponse.status).toBe(204);

    const otpVerifyResponse = await fetchHandler(
      new Request("http://example.com/api/auth/otp/verify", {
        method: "POST",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({
          email: "abun@tireless.dev",
          otp: "424242",
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(otpVerifyResponse.status).toBe(200);
    expect(await otpVerifyResponse.json()).toEqual({
      access_token: "uid:abun",
      user_id: "abun",
    });
  });
});

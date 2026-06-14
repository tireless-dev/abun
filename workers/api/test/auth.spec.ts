import { describe, expect, it } from "vitest";
import worker from "../src/index";

describe("api worker auth sessions", () => {
  it("verifies otp through generic auth routes and returns a full session payload", async () => {
    const { fetchHandler, env } = createHarness();

    const requestResponse = await fetchHandler(
      new Request("http://example.com/auth/request", {
        method: "POST",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({
          method: "otp_email",
          email: "  ABUN@TIRELESS.DEV  ",
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(requestResponse.status).toBe(204);

    const verifyResponse = await fetchHandler(
      new Request("http://example.com/auth/verify", {
        method: "POST",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({
          method: "otp_email",
          email: "abun@tireless.dev",
          otp: "424242",
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(verifyResponse.status).toBe(200);

    const payload = await verifyResponse.json<SessionPayload>();
    expect(payload.user_id).toBe("abun");
    expect(payload.access_token).toEqual(expect.any(String));
    expect(payload.access_token).not.toBe("uid:abun");
    expect(payload.refresh_token).toEqual(expect.any(String));
    expect(payload.refresh_token).not.toBe(payload.access_token);
    expect(Date.parse(payload.access_token_expires_at)).toBeGreaterThan(Date.now());
    expect(Date.parse(payload.refresh_token_expires_at)).toBeGreaterThan(Date.parse(payload.access_token_expires_at));
  });

  it("rotates the refresh token and invalidates the previous one", async () => {
    const { fetchHandler, env } = createHarness();
    const session = await login(fetchHandler, env, "abun@tireless.dev");

    const refreshResponse = await fetchHandler(
      new Request("http://example.com/auth/refresh", {
        method: "POST",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({
          refresh_token: session.refresh_token,
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(refreshResponse.status).toBe(200);
    const rotated = await refreshResponse.json<SessionPayload>();
    expect(rotated.user_id).toBe(session.user_id);
    expect(rotated.refresh_token).not.toBe(session.refresh_token);
    expect(rotated.access_token).not.toBe(session.access_token);

    const staleRefreshResponse = await fetchHandler(
      new Request("http://example.com/auth/refresh", {
        method: "POST",
        headers: {
          "content-type": "application/json",
        },
        body: JSON.stringify({
          refresh_token: session.refresh_token,
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(staleRefreshResponse.status).toBe(401);
    expect(await staleRefreshResponse.json()).toEqual({
      message: "Invalid refresh token",
    });
  });

  it("revokes only the current session on logout while leaving other device sessions valid", async () => {
    const { fetchHandler, env } = createHarness();
    const firstSession = await login(fetchHandler, env, "abun@tireless.dev");
    const secondSession = await login(fetchHandler, env, "abun@tireless.dev");

    const logoutResponse = await fetchHandler(
      new Request("http://example.com/auth/logout", {
        method: "POST",
        headers: {
          "content-type": "application/json",
          authorization: `Bearer ${firstSession.access_token}`,
        },
        body: JSON.stringify({
          refresh_token: firstSession.refresh_token,
        }),
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(logoutResponse.status).toBe(204);

    const revokedSyncResponse = await fetchHandler(
      new Request("http://example.com/api/tasks", {
        headers: {
          authorization: `Bearer ${firstSession.access_token}`,
        },
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(revokedSyncResponse.status).toBe(401);
    expect(await revokedSyncResponse.json()).toEqual({
      message: "Session revoked",
    });

    const otherDeviceResponse = await fetchHandler(
      new Request("http://example.com/api/tasks", {
        headers: {
          authorization: `Bearer ${secondSession.access_token}`,
        },
      }) as never,
      env,
      {} as ExecutionContext,
    );

    expect(otherDeviceResponse.status).toBe(200);
    expect(await otherDeviceResponse.json()).toEqual([]);
  });
});

function createHarness() {
  const handler = worker as ExportedHandler;
  return {
    fetchHandler: handler.fetch as NonNullable<ExportedHandler["fetch"]>,
    env: { ABUN_REQUIRE_AUTH: "true" } as never,
  };
}

async function login(
  fetchHandler: NonNullable<ExportedHandler["fetch"]>,
  env: never,
  email: string,
): Promise<SessionPayload> {
  const normalizedEmail = email.trim().toLowerCase();

  const requestResponse = await fetchHandler(
    new Request("http://example.com/auth/request", {
      method: "POST",
      headers: {
        "content-type": "application/json",
      },
      body: JSON.stringify({
        method: "otp_email",
        email: normalizedEmail,
      }),
    }) as never,
    env,
    {} as ExecutionContext,
  );

  expect(requestResponse.status).toBe(204);

  const verifyResponse = await fetchHandler(
    new Request("http://example.com/auth/verify", {
      method: "POST",
      headers: {
        "content-type": "application/json",
      },
      body: JSON.stringify({
        method: "otp_email",
        email: normalizedEmail,
        otp: "424242",
      }),
    }) as never,
    env,
    {} as ExecutionContext,
  );

  expect(verifyResponse.status).toBe(200);
  return verifyResponse.json<SessionPayload>();
}

interface SessionPayload {
  user_id: string;
  access_token: string;
  access_token_expires_at: string;
  refresh_token: string;
  refresh_token_expires_at: string;
}

import { describe, expect, it, vi } from 'vitest';
import { createAbunApiClient, formatApiVersion } from './client.ts';

describe('createAbunApiClient auth contract', () => {
  it('uses the generic auth routes and otp_email payloads', async () => {
    const fetchImpl = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(_input);
      if (url.endsWith('/auth/request')) {
        expect(init?.method).toBe('POST');
        expect(JSON.parse(String(init?.body))).toEqual({
          method: 'otp_email',
          email: 'abun@tireless.dev',
        });
        return new Response(null, { status: 204 });
      }

      if (url.endsWith('/auth/verify')) {
        expect(init?.method).toBe('POST');
        expect(JSON.parse(String(init?.body))).toEqual({
          method: 'otp_email',
          email: 'abun@tireless.dev',
          otp: '424242',
        });
        return Response.json({
          access_token: 'access-1',
          access_token_expires_at: '2030-01-01T00:15:00Z',
          refresh_token: 'refresh-1',
          refresh_token_expires_at: '2030-02-01T00:00:00Z',
          user_id: 'abun',
        });
      }

      throw new Error(`Unexpected URL: ${url}`);
    });

    const client = createAbunApiClient({
      baseUrl: 'https://abun.tireless.dev',
      fetchImpl: fetchImpl as typeof fetch,
    });

    await client.requestOtp('abun@tireless.dev');
    const response = await client.verifyOtp('abun@tireless.dev', '424242');

    expect(response.user_id).toBe('abun');
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it('refreshes and retries once after a 401 on protected requests', async () => {
    const fetchImpl = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(_input);
      const authHeader = new Headers(init?.headers).get('authorization');

      if (url.endsWith('/api/tasks') && authHeader === 'Bearer expired-access') {
        return Response.json({ message: 'Missing or invalid bearer token' }, { status: 401 });
      }

      if (url.endsWith('/api/tasks') && authHeader === 'Bearer refreshed-access') {
        return Response.json([]);
      }

      throw new Error(`Unexpected request ${url} ${authHeader ?? ''}`);
    });

    const refreshBearerToken = vi.fn(async () => 'refreshed-access');

    const client = createAbunApiClient({
      baseUrl: 'https://abun.tireless.dev',
      fetchImpl: fetchImpl as typeof fetch,
      getBearerToken: async () => 'expired-access',
      refreshBearerToken,
    });

    expect(await client.listTasks()).toEqual([]);
    expect(refreshBearerToken).toHaveBeenCalledTimes(1);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });
  it("fetches api version without requiring auth", async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      expect(String(input)).toBe("https://abun.tireless.dev/api/version");
      return Response.json({ version: "0.0.0", name: "api" });
    });

    const client = createAbunApiClient({
      baseUrl: "https://abun.tireless.dev",
      fetchImpl: fetchImpl as typeof fetch,
    });

    const res = await client.getVersion();
    expect(res).toEqual({ version: "0.0.0", name: "api" });
  });

  it("formats clean and dirty api versions", () => {
    expect(formatApiVersion({ version: "0.0.0", commit: "470a79a", buildTime: "202611081035" }))
      .toBe("0.0.0-202611081035-470a79a");
    expect(formatApiVersion({ version: "0.0.0", commit: "wip", buildTime: "202611081220" }))
      .toBe("0.0.0-202611081220-wip");
    expect(formatApiVersion({ version: "0.0.0" }))
      .toBe("0.0.0");
  });
});

import { describe, expect, it, vi } from "vitest";
import { SupabaseAuthService } from "../src/services/auth-service";

describe("SupabaseAuthService", () => {
  const supabaseConfig = {
    url: "https://example.supabase.co",
    secretKey: "test-service-role-key",
    publishableKey: "test-publishable-key",
  };

  const localConfig = {
    jwtSecret: "test-jwt-secret-for-specs",
    accessTokenTtlSeconds: 900,
    refreshTokenTtlSeconds: 2592000,
    now: () => 1700000000000,
  };

  it("throws 400 when Supabase rejects verification with invalid code", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ msg: "Token has expired or is invalid" }),
    });
    vi.stubGlobal("fetch", fetchMock);

    try {
      const service = new SupabaseAuthService(supabaseConfig, localConfig);
      await expect(
        service.verifyAuth("otp_email", "person@example.com", "999999"),
      ).rejects.toThrow("Token has expired or is invalid");
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("throws 401 when Supabase rejects refresh token", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({ error_description: "Invalid Refresh Token" }),
    });
    vi.stubGlobal("fetch", fetchMock);

    try {
      const service = new SupabaseAuthService(supabaseConfig, localConfig);
      await expect(
        service.refreshSession("invalid-refresh-token"),
      ).rejects.toThrow("The login session is invalid or expired.");
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("requests OTP via Supabase Auth API for real emails", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({}),
    });
    vi.stubGlobal("fetch", fetchMock);

    try {
      const service = new SupabaseAuthService(supabaseConfig, localConfig);
      await service.requestAuth("otp_email", "jerryshang@gmail.com");

      expect(fetchMock).toHaveBeenCalledTimes(1);
      const [url, options] = fetchMock.mock.calls[0];
      expect(url).toBe("https://example.supabase.co/auth/v1/otp");
      expect(options.method).toBe("POST");
      expect(options.headers.apikey).toBe("test-service-role-key");
      expect(options.headers.Authorization).toBe("Bearer test-service-role-key");
      expect(JSON.parse(options.body)).toEqual({
        email: "jerryshang@gmail.com",
        create_user: true,
      });
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("verifies OTP via Supabase Auth and returns native session", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        access_token: "sb-access-token-123",
        refresh_token: "sb-refresh-token-456",
        expires_in: 3600,
        expires_at: 1700003600,
        user: {
          id: "4a9cbf01-1444-431c-b28f-63d8546ae253",
          email: "jerryshang@gmail.com",
        },
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    try {
      const service = new SupabaseAuthService(supabaseConfig, localConfig);
      const session = await service.verifyAuth("otp_email", "jerryshang@gmail.com", "123456");

      expect(fetchMock).toHaveBeenCalledTimes(1);
      const [url, options] = fetchMock.mock.calls[0];
      expect(url).toBe("https://example.supabase.co/auth/v1/verify");
      expect(options.method).toBe("POST");
      expect(JSON.parse(options.body)).toEqual({
        email: "jerryshang@gmail.com",
        token: "123456",
        type: "email",
      });

      expect(session.userId).toBe("4a9cbf01-1444-431c-b28f-63d8546ae253");
      expect(session.accessToken).toBe("sb-access-token-123");
      expect(session.refreshToken).toBe("sb-refresh-token-456");
      expect(session.accessTokenExpiresAt).toBe(new Date(1700003600 * 1000).toISOString());
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("refreshes Supabase session via grant_type=refresh_token", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        access_token: "sb-access-token-fresh",
        refresh_token: "sb-refresh-token-fresh",
        expires_in: 3600,
        expires_at: 1700007200,
        user: {
          id: "4a9cbf01-1444-431c-b28f-63d8546ae253",
          email: "jerryshang@gmail.com",
        },
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    try {
      const service = new SupabaseAuthService(supabaseConfig, localConfig);
      const session = await service.refreshSession("sb-refresh-token-456");

      expect(fetchMock).toHaveBeenCalledTimes(1);
      const [url, options] = fetchMock.mock.calls[0];
      expect(url).toBe("https://example.supabase.co/auth/v1/token?grant_type=refresh_token");
      expect(options.method).toBe("POST");
      expect(JSON.parse(options.body)).toEqual({
        refresh_token: "sb-refresh-token-456",
      });

      expect(session.accessToken).toBe("sb-access-token-fresh");
      expect(session.refreshToken).toBe("sb-refresh-token-fresh");
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("authenticates bearer token against /auth/v1/user and caches resolution", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        id: "4a9cbf01-1444-431c-b28f-63d8546ae253",
        email: "jerryshang@gmail.com",
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    try {
      const service = new SupabaseAuthService(supabaseConfig, localConfig);

      // First call: hits Supabase API
      const session1 = await service.authenticateAccessToken("sb-access-token-valid");
      expect(session1.userId).toBe("4a9cbf01-1444-431c-b28f-63d8546ae253");
      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(fetchMock.mock.calls[0][0]).toBe("https://example.supabase.co/auth/v1/user");
      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer sb-access-token-valid");

      // Second call: hits in-memory cache, no new fetch call
      const session2 = await service.authenticateAccessToken("sb-access-token-valid");
      expect(session2.userId).toBe("4a9cbf01-1444-431c-b28f-63d8546ae253");
      expect(fetchMock).toHaveBeenCalledTimes(1);

      // Logout evicts from cache
      await service.logoutSession({ accessToken: "sb-access-token-valid" });
      const session3 = await service.authenticateAccessToken("sb-access-token-valid");
      expect(session3.userId).toBe("4a9cbf01-1444-431c-b28f-63d8546ae253");
      expect(fetchMock).toHaveBeenCalledTimes(3); // 1 for user, 1 for logout, 1 for re-check
    } finally {
      vi.unstubAllGlobals();
    }
  });
});

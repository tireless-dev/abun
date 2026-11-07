import type { DbClient } from "../db/transaction";
import { HttpError } from "../http/errors";
import type { WorkerEnv } from "../env";

const SHARED_TEST_ACCOUNT = {
  email: "abun@tireless.dev",
  otp: "424242",
  userId: "abun",
} as const;

const OTP_EMAIL_METHOD = "otp_email";
const ACCESS_TOKEN_TTL_SECONDS = 15 * 60;
const REFRESH_TOKEN_TTL_SECONDS = 30 * 24 * 60 * 60;
const DEFAULT_JWT_SECRET = "abun-local-auth-secret";

interface PendingOtp {
  code: string;
  expiresAt: number;
}

interface SessionRecord {
  id: string;
  userId: string;
  refreshTokenHash: string;
  refreshExpiresAt: number;
  revokedAt: number | null;
  rotatedAt: number | null;
  createdAt: number;
  updatedAt: number;
}

interface AccessTokenClaims {
  sub: string;
  sid: string;
  jti: string;
  iat: number;
  exp: number;
  typ: "access";
}

export interface AuthenticatedSession {
  sessionId: string;
  userId: string;
}

export interface AuthSessionResult {
  userId: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

export interface LogoutRequest {
  refreshToken?: string | null;
  accessToken?: string | null;
}

export interface AuthServiceLike {
  requestAuth(method: string, email: string): Promise<void> | void;
  verifyAuth(method: string, email: string, otp: string): Promise<AuthSessionResult> | AuthSessionResult;
  refreshSession(refreshToken: string): Promise<AuthSessionResult> | AuthSessionResult;
  logoutSession(request: LogoutRequest): Promise<void> | void;
  authenticateAccessToken(accessToken: string): Promise<AuthenticatedSession> | AuthenticatedSession;
}

export class AuthService implements AuthServiceLike {
  private readonly otpCodes = new Map<string, PendingOtp>();
  private readonly userIdsByEmail = new Map<string, string>();
  private readonly sessions = new Map<string, SessionRecord>();

  constructor(private readonly config: AuthConfig) {}

  requestAuth(method: string, email: string): void {
    requireOtpEmailMethod(method);
    const normalized = normalizeEmail(email);
    const code =
      normalized === SHARED_TEST_ACCOUNT.email
        ? SHARED_TEST_ACCOUNT.otp
        : generateOtp();

    this.otpCodes.set(normalized, {
      code,
      expiresAt: this.config.now() + 10 * 60 * 1000,
    });
  }

  async verifyAuth(method: string, email: string, otp: string): Promise<AuthSessionResult> {
    requireOtpEmailMethod(method);
    const normalized = normalizeEmail(email);
    const pending = this.otpCodes.get(normalized);

    if (!pending) {
      throw new HttpError(400, "otp not requested");
    }

    if (pending.code !== otp.trim()) {
      throw new HttpError(400, "invalid otp");
    }

    if (pending.expiresAt <= this.config.now()) {
      throw new HttpError(400, "otp expired");
    }

    this.otpCodes.delete(normalized);

    const userId = this.ensureUser(normalized);
    return issueSession({
      config: this.config,
      userId,
      persistSession: async (session) => {
        this.sessions.set(session.id, session);
      },
    });
  }

  async refreshSession(refreshToken: string): Promise<AuthSessionResult> {
    const sessionId = readRefreshTokenSessionId(refreshToken);
    const session = this.sessions.get(sessionId);
    if (!session) {
      throw new HttpError(401, "Invalid refresh token");
    }

    return rotateSession({
      config: this.config,
      refreshToken,
      session,
      persistSession: async (nextSession) => {
        this.sessions.set(nextSession.id, nextSession);
      },
    });
  }

  async logoutSession(request: LogoutRequest): Promise<void> {
    const sessionId = await resolveSessionIdForLogout(request, this.config);
    if (!sessionId) {
      return;
    }

    const existing = this.sessions.get(sessionId);
    if (!existing) {
      return;
    }

    this.sessions.set(sessionId, {
      ...existing,
      revokedAt: this.config.now(),
      updatedAt: this.config.now(),
    });
  }

  async authenticateAccessToken(accessToken: string): Promise<AuthenticatedSession> {
    const claims = await verifyAccessToken(accessToken, this.config);
    const session = this.sessions.get(claims.sid);

    if (!session) {
      throw new HttpError(401, "Missing or invalid bearer token");
    }

    if (session.revokedAt !== null) {
      throw new HttpError(401, "Session revoked");
    }

    if (session.userId !== claims.sub) {
      throw new HttpError(401, "Missing or invalid bearer token");
    }

    return {
      sessionId: session.id,
      userId: session.userId,
    };
  }

  private ensureUser(email: string): string {
    const existingUserId = this.userIdsByEmail.get(email);
    if (existingUserId) {
      return existingUserId;
    }

    const userId =
      email === SHARED_TEST_ACCOUNT.email
        ? SHARED_TEST_ACCOUNT.userId
        : crypto.randomUUID();

    this.userIdsByEmail.set(email, userId);
    return userId;
  }
}

export interface SupabaseAuthConfig {
  url: string;
  secretKey: string;
  publishableKey?: string;
}

export class SupabaseAuthService implements AuthServiceLike {
  private readonly tokenCache = new Map<string, { userId: string; expiresAt: number }>();

  constructor(
    private readonly supabaseConfig: SupabaseAuthConfig,
    private readonly localConfig: AuthConfig,
  ) {}

  async requestAuth(method: string, email: string): Promise<void> {
    requireOtpEmailMethod(method);
    const normalized = normalizeEmail(email);

    const response = await fetch(`${normalizeUrl(this.supabaseConfig.url)}/auth/v1/otp`, {
      method: "POST",
      headers: {
        apikey: this.supabaseConfig.secretKey,
        Authorization: `Bearer ${this.supabaseConfig.secretKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email: normalized,
        create_user: true,
      }),
    });

    if (!response.ok) {
      const err = await response.json().catch(() => null) as { msg?: string; message?: string; error_description?: string } | null;
      throw new HttpError(
        response.status >= 500 ? 502 : 400,
        err?.msg || err?.message || err?.error_description || "The verification code could not be sent.",
      );
    }
  }

  async verifyAuth(method: string, email: string, otp: string): Promise<AuthSessionResult> {
    requireOtpEmailMethod(method);
    const normalized = normalizeEmail(email);

    const response = await fetch(`${normalizeUrl(this.supabaseConfig.url)}/auth/v1/verify`, {
      method: "POST",
      headers: {
        apikey: this.supabaseConfig.secretKey,
        Authorization: `Bearer ${this.supabaseConfig.secretKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email: normalized,
        token: otp.trim(),
        type: "email",
      }),
    });

    if (!response.ok) {
      const err = await response.json().catch(() => null) as { msg?: string; message?: string; error_description?: string } | null;
      throw new HttpError(
        400,
        err?.msg || err?.message || err?.error_description || "The verification code is invalid or expired.",
      );
    }

    const result = await response.json() as {
      access_token: string;
      refresh_token: string;
      expires_in: number;
      expires_at?: number;
      user: { id: string; email: string };
    };

    if (!result || typeof result.access_token !== "string" || !result.user?.id) {
      throw new HttpError(502, "The verification response was incomplete.");
    }

    const expiresAt = result.expires_at
      ? result.expires_at * 1000
      : this.localConfig.now() + (result.expires_in ?? 3600) * 1000;

    this.cacheVerifiedToken(result.access_token, result.user.id, expiresAt);

    return {
      userId: result.user.id,
      accessToken: result.access_token,
      accessTokenExpiresAt: new Date(expiresAt).toISOString(),
      refreshToken: result.refresh_token,
      refreshTokenExpiresAt: new Date(this.localConfig.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
    };
  }

  async refreshSession(refreshToken: string): Promise<AuthSessionResult> {
    const response = await fetch(`${normalizeUrl(this.supabaseConfig.url)}/auth/v1/token?grant_type=refresh_token`, {
      method: "POST",
      headers: {
        apikey: this.supabaseConfig.secretKey,
        Authorization: `Bearer ${this.supabaseConfig.secretKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ refresh_token: refreshToken.trim() }),
    });

    if (!response.ok) {
      throw new HttpError(401, "The login session is invalid or expired.");
    }

    const result = await response.json() as {
      access_token: string;
      refresh_token: string;
      expires_in: number;
      expires_at?: number;
      user: { id: string; email: string };
    };

    if (!result || typeof result.access_token !== "string" || !result.user?.id) {
      throw new HttpError(401, "The refresh response was incomplete.");
    }

    const expiresAt = result.expires_at
      ? result.expires_at * 1000
      : this.localConfig.now() + (result.expires_in ?? 3600) * 1000;

    this.cacheVerifiedToken(result.access_token, result.user.id, expiresAt);

    return {
      userId: result.user.id,
      accessToken: result.access_token,
      accessTokenExpiresAt: new Date(expiresAt).toISOString(),
      refreshToken: result.refresh_token,
      refreshTokenExpiresAt: new Date(this.localConfig.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
    };
  }

  async logoutSession(request: LogoutRequest): Promise<void> {
    if (request.accessToken) {
      this.tokenCache.delete(request.accessToken.trim());
      await fetch(`${normalizeUrl(this.supabaseConfig.url)}/auth/v1/logout`, {
        method: "POST",
        headers: {
          apikey: this.supabaseConfig.secretKey,
          Authorization: `Bearer ${request.accessToken.trim()}`,
        },
      }).catch(() => {});
    }
  }

  async authenticateAccessToken(accessToken: string): Promise<AuthenticatedSession> {
    const trimmed = accessToken.trim();

    const cached = this.tokenCache.get(trimmed);
    if (cached && cached.expiresAt > this.localConfig.now()) {
      return { sessionId: "supabase", userId: cached.userId };
    }

    const response = await fetch(`${normalizeUrl(this.supabaseConfig.url)}/auth/v1/user`, {
      headers: {
        apikey: this.supabaseConfig.secretKey,
        Authorization: `Bearer ${trimmed}`,
      },
    });

    if (!response.ok) {
      throw new HttpError(401, "Missing or invalid bearer token");
    }

    const user = await response.json() as { id: string; email?: string };
    if (!user?.id) {
      throw new HttpError(401, "Missing or invalid bearer token");
    }

    this.cacheVerifiedToken(trimmed, user.id, this.localConfig.now() + 60_000);
    return {
      sessionId: "supabase",
      userId: user.id,
    };
  }

  private cacheVerifiedToken(token: string, userId: string, expiresAt: number): void {
    if (this.tokenCache.size > 1000) {
      this.tokenCache.clear();
    }
    this.tokenCache.set(token, {
      userId,
      expiresAt: Math.min(expiresAt, this.localConfig.now() + 60_000),
    });
  }
}

let defaultAuthService: AuthServiceLike | null = null;

export function resolveAuthService(
  env: Record<string, unknown>,
  dbClient?: DbClient,
): AuthServiceLike {
  const injected = env.__authService;

  if (isAuthServiceLike(injected)) {
    return injected;
  }

  const workerEnv = env as Partial<WorkerEnv>;
  if (workerEnv.SUPABASE_URL && workerEnv.SUPABASE_SECRET_KEY) {
    return new SupabaseAuthService(
      {
        url: workerEnv.SUPABASE_URL,
        secretKey: workerEnv.SUPABASE_SECRET_KEY,
        publishableKey: workerEnv.SUPABASE_PUBLISHABLE_KEY,
      },
      createAuthConfig(workerEnv),
    );
  }

  const config = createAuthConfig(workerEnv);
  defaultAuthService ??= new AuthService(config);
  return defaultAuthService;
}

function isAuthServiceLike(value: unknown): value is AuthServiceLike {
  return (
    typeof value === "object" &&
    value !== null &&
    "requestAuth" in value &&
    typeof value.requestAuth === "function" &&
    "verifyAuth" in value &&
    typeof value.verifyAuth === "function" &&
    "refreshSession" in value &&
    typeof value.refreshSession === "function" &&
    "logoutSession" in value &&
    typeof value.logoutSession === "function" &&
    "authenticateAccessToken" in value &&
    typeof value.authenticateAccessToken === "function"
  );
}

function createAuthConfig(env: Partial<WorkerEnv>): AuthConfig {
  const accessTokenTtlSeconds = Number(env.AUTH_ACCESS_TOKEN_TTL_SECONDS ?? ACCESS_TOKEN_TTL_SECONDS);
  const refreshTokenTtlSeconds = Number(env.AUTH_REFRESH_TOKEN_TTL_SECONDS ?? REFRESH_TOKEN_TTL_SECONDS);

  return {
    jwtSecret: typeof env.AUTH_JWT_SECRET === "string" && env.AUTH_JWT_SECRET.length > 0
      ? env.AUTH_JWT_SECRET
      : DEFAULT_JWT_SECRET,
    accessTokenTtlSeconds: Number.isFinite(accessTokenTtlSeconds) && accessTokenTtlSeconds > 0
      ? Math.trunc(accessTokenTtlSeconds)
      : ACCESS_TOKEN_TTL_SECONDS,
    refreshTokenTtlSeconds: Number.isFinite(refreshTokenTtlSeconds) && refreshTokenTtlSeconds > 0
      ? Math.trunc(refreshTokenTtlSeconds)
      : REFRESH_TOKEN_TTL_SECONDS,
    now: () => Date.now(),
  };
}

interface AuthConfig {
  jwtSecret: string;
  accessTokenTtlSeconds: number;
  refreshTokenTtlSeconds: number;
  now: () => number;
}

async function issueSession(input: {
  config: AuthConfig;
  userId: string;
  persistSession: (session: SessionRecord) => Promise<void>;
}): Promise<AuthSessionResult> {
  const now = input.config.now();
  const sessionId = crypto.randomUUID();
  const refreshToken = createRefreshToken(sessionId);
  const session: SessionRecord = {
    id: sessionId,
    userId: input.userId,
    refreshTokenHash: await hashToken(refreshToken),
    refreshExpiresAt: now + input.config.refreshTokenTtlSeconds * 1000,
    revokedAt: null,
    rotatedAt: null,
    createdAt: now,
    updatedAt: now,
  };

  await input.persistSession(session);
  return buildSessionResponse(input.config, session, refreshToken);
}

async function rotateSession(input: {
  config: AuthConfig;
  refreshToken: string;
  session: SessionRecord;
  persistSession: (session: SessionRecord) => Promise<void>;
}): Promise<AuthSessionResult> {
  await validateRefreshToken(input);

  const now = input.config.now();
  const nextRefreshToken = createRefreshToken(input.session.id);
  const nextSession: SessionRecord = {
    ...input.session,
    refreshTokenHash: await hashToken(nextRefreshToken),
    refreshExpiresAt: now + input.config.refreshTokenTtlSeconds * 1000,
    rotatedAt: now,
    updatedAt: now,
  };

  await input.persistSession(nextSession);
  return buildSessionResponse(input.config, nextSession, nextRefreshToken);
}

async function buildSessionResponse(
  config: AuthConfig,
  session: SessionRecord,
  refreshToken: string,
): Promise<AuthSessionResult> {
  const nowSeconds = Math.floor(config.now() / 1000);
  const accessExpiresAtSeconds = nowSeconds + config.accessTokenTtlSeconds;
  const accessToken = await signAccessToken(
    {
      sub: session.userId,
      sid: session.id,
      jti: crypto.randomUUID(),
      iat: nowSeconds,
      exp: accessExpiresAtSeconds,
      typ: "access",
    },
    config,
  );

  return {
    userId: session.userId,
    accessToken,
    accessTokenExpiresAt: new Date(accessExpiresAtSeconds * 1000).toISOString(),
    refreshToken,
    refreshTokenExpiresAt: new Date(session.refreshExpiresAt).toISOString(),
  };
}

async function validateRefreshToken(input: {
  config: AuthConfig;
  refreshToken: string;
  session: SessionRecord | null | undefined;
}): Promise<void> {
  const { refreshToken, session, config } = input;

  if (!session) {
    throw new HttpError(401, "Invalid refresh token");
  }

  if (session.revokedAt !== null) {
    throw new HttpError(401, "Session revoked");
  }

  if (session.refreshExpiresAt <= config.now()) {
    throw new HttpError(401, "Refresh token expired");
  }

  const refreshTokenHash = await hashToken(refreshToken);
  if (refreshTokenHash !== session.refreshTokenHash) {
    throw new HttpError(401, "Invalid refresh token");
  }
}

async function resolveSessionIdForLogout(
  request: LogoutRequest,
  config: AuthConfig,
): Promise<string | null> {
  if (typeof request.refreshToken === "string" && request.refreshToken.length > 0) {
    return readRefreshTokenSessionId(request.refreshToken);
  }

  if (typeof request.accessToken === "string" && request.accessToken.length > 0) {
    const claims = await verifyAccessToken(request.accessToken, config, { ignoreExpiration: true });
    return claims.sid;
  }

  return null;
}

function requireOtpEmailMethod(method: string): void {
  if (method.trim() !== OTP_EMAIL_METHOD) {
    throw new HttpError(400, "Unsupported auth method");
  }
}

function normalizeEmail(email: string): string {
  const normalized = email.trim().toLowerCase();

  if (normalized.length === 0) {
    throw new HttpError(400, "email is required");
  }

  return normalized;
}

function normalizeUrl(url: string): string {
  return url.replace(/\/+$/, "");
}

function generateOtp(): string {
  const random = new Uint32Array(1);
  crypto.getRandomValues(random);
  return `${100000 + (random[0] % 900000)}`;
}

function createRefreshToken(sessionId: string): string {
  return `${sessionId}.${randomBase64Url(32)}`;
}

function readRefreshTokenSessionId(refreshToken: string): string {
  const [sessionId, secret] = refreshToken.trim().split(".", 2);

  if (!sessionId || !secret) {
    throw new HttpError(401, "Invalid refresh token");
  }

  return sessionId;
}

async function signAccessToken(
  claims: AccessTokenClaims,
  config: AuthConfig,
): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const encodedHeader = base64UrlEncodeJson(header);
  const encodedClaims = base64UrlEncodeJson(claims);
  const signature = await signJwtPayload(
    `${encodedHeader}.${encodedClaims}`,
    config.jwtSecret,
  );
  return `${encodedHeader}.${encodedClaims}.${signature}`;
}

async function verifyAccessToken(
  token: string,
  config: AuthConfig,
  options: { ignoreExpiration?: boolean } = {},
): Promise<AccessTokenClaims> {
  const parts = token.trim().split(".");
  if (parts.length !== 3) {
    throw new HttpError(401, "Missing or invalid bearer token");
  }

  const [encodedHeader, encodedClaims, encodedSignature] = parts;
  const header = decodeBase64UrlJson<Record<string, unknown>>(encodedHeader);
  if (header.alg !== "HS256" || header.typ !== "JWT") {
    throw new HttpError(401, "Missing or invalid bearer token");
  }

  const isValidSignature = await verifyJwtPayload(
    `${encodedHeader}.${encodedClaims}`,
    encodedSignature,
    config.jwtSecret,
  );
  if (!isValidSignature) {
    throw new HttpError(401, "Missing or invalid bearer token");
  }

  const claims = decodeBase64UrlJson<AccessTokenClaims>(encodedClaims);
  if (claims.typ !== "access" || typeof claims.sub !== "string" || typeof claims.sid !== "string") {
    throw new HttpError(401, "Missing or invalid bearer token");
  }

  if (!options.ignoreExpiration && claims.exp * 1000 <= config.now()) {
    throw new HttpError(401, "Missing or invalid bearer token");
  }

  return claims;
}

async function signJwtPayload(
  payload: string,
  secret: string,
): Promise<string> {
  const signature = await crypto.subtle.sign(
    "HMAC",
    await importHmacKey(secret, ["sign"]),
    new TextEncoder().encode(payload),
  );
  return base64UrlEncode(new Uint8Array(signature));
}

async function verifyJwtPayload(
  payload: string,
  signature: string,
  secret: string,
): Promise<boolean> {
  return crypto.subtle.verify(
    "HMAC",
    await importHmacKey(secret, ["verify"]),
    Buffer.from(base64UrlDecode(signature)),
    new TextEncoder().encode(payload),
  );
}

async function importHmacKey(
  secret: string,
  usages: KeyUsage[],
): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    usages,
  );
}

async function hashToken(value: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(value),
  );
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function base64UrlEncodeJson(value: unknown): string {
  return base64UrlEncode(new TextEncoder().encode(JSON.stringify(value)));
}

function decodeBase64UrlJson<T>(value: string): T {
  try {
    return JSON.parse(new TextDecoder().decode(base64UrlDecode(value))) as T;
  } catch {
    throw new HttpError(401, "Missing or invalid bearer token");
  }
}

function base64UrlEncode(value: Uint8Array): string {
  return Buffer.from(value)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function base64UrlDecode(value: string): Uint8Array {
  const padded = value
    .replace(/-/g, "+")
    .replace(/_/g, "/")
    .padEnd(value.length + (4 - (value.length % 4 || 4)) % 4, "=");

  return new Uint8Array(Buffer.from(padded, "base64"));
}

function randomBase64Url(bytes: number): string {
  const value = new Uint8Array(bytes);
  crypto.getRandomValues(value);
  return base64UrlEncode(value);
}

import type { DbClient } from "../db/transaction";
import { withTransaction } from "../db/transaction";
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

export class DatabaseAuthService implements AuthServiceLike {
  constructor(
    private readonly db: DbClient,
    private readonly config: AuthConfig,
  ) {}

  async requestAuth(method: string, email: string): Promise<void> {
    requireOtpEmailMethod(method);
    const normalized = normalizeEmail(email);
    const code =
      normalized === SHARED_TEST_ACCOUNT.email
        ? SHARED_TEST_ACCOUNT.otp
        : generateOtp();
    const now = new Date(this.config.now());

    await withTransaction(this.db, async (tx) => {
      await tx.query(
        `
          insert into otp_code(email, code, expires_at, created_at)
          values ($1, $2, $3, $4)
          on conflict (email)
          do update set code = excluded.code, expires_at = excluded.expires_at, created_at = excluded.created_at
        `,
        [
          normalized,
          code,
          new Date(now.getTime() + 10 * 60 * 1000).toISOString(),
          now.toISOString(),
        ],
      );
    });
  }

  async verifyAuth(method: string, email: string, otp: string): Promise<AuthSessionResult> {
    requireOtpEmailMethod(method);
    const normalized = normalizeEmail(email);

    return withTransaction(this.db, async (tx) => {
      const pendingResult = await tx.query(
        `select code, expires_at from otp_code where email = $1`,
        [normalized],
      );
      const pending = pendingResult.rows[0];

      if (!pending) {
        throw new HttpError(400, "otp not requested");
      }

      if (String(pending.code) !== otp.trim()) {
        throw new HttpError(400, "invalid otp");
      }

      if (Date.parse(String(pending.expires_at)) <= this.config.now()) {
        throw new HttpError(400, "otp expired");
      }

      const userId = await ensureDatabaseUser(tx, normalized);
      await tx.query(`delete from otp_code where email = $1`, [normalized]);

      return issueSession({
        config: this.config,
        userId,
        persistSession: async (session) => {
          await tx.query(
            `
              insert into user_session(
                id,
                user_id,
                refresh_token_hash,
                refresh_expires_at,
                revoked_at,
                rotated_at,
                created_at,
                updated_at
              )
              values ($1, $2, $3, $4, $5, $6, $7, $8)
            `,
            [
              session.id,
              session.userId,
              session.refreshTokenHash,
              iso(session.refreshExpiresAt),
              null,
              null,
              iso(session.createdAt),
              iso(session.updatedAt),
            ],
          );
        },
      });
    });
  }

  async refreshSession(refreshToken: string): Promise<AuthSessionResult> {
    const sessionId = readRefreshTokenSessionId(refreshToken);

    return withTransaction(this.db, async (tx) => {
      const session = await selectDatabaseSession(tx, sessionId);
      if (!session) {
        throw new HttpError(401, "Invalid refresh token");
      }

      return rotateSession({
        config: this.config,
        refreshToken,
        session,
        persistSession: async (nextSession) => {
          await tx.query(
            `
              update user_session
              set refresh_token_hash = $2,
                  refresh_expires_at = $3,
                  rotated_at = $4,
                  updated_at = $5
              where id = $1
            `,
            [
              nextSession.id,
              nextSession.refreshTokenHash,
              iso(nextSession.refreshExpiresAt),
              iso(nextSession.rotatedAt),
              iso(nextSession.updatedAt),
            ],
          );
        },
      });
    });
  }

  async logoutSession(request: LogoutRequest): Promise<void> {
    const sessionId = await resolveSessionIdForLogout(request, this.config);
    if (!sessionId) {
      return;
    }

    await withTransaction(this.db, async (tx) => {
      await tx.query(
        `
          update user_session
          set revoked_at = $2,
              updated_at = $2
          where id = $1 and revoked_at is null
        `,
        [sessionId, iso(this.config.now())],
      );
    });
  }

  async authenticateAccessToken(accessToken: string): Promise<AuthenticatedSession> {
    const claims = await verifyAccessToken(accessToken, this.config);
    const session = await selectDatabaseSession(this.db, claims.sid);

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

  const config = createAuthConfig(env as Partial<WorkerEnv>);

  if (dbClient) {
    return new DatabaseAuthService(dbClient, config);
  }

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

async function selectDatabaseSession(
  db: DbClient,
  sessionId: string,
): Promise<SessionRecord | null> {
  const result = await db.query(
    `
      select
        id,
        user_id,
        refresh_token_hash,
        refresh_expires_at,
        revoked_at,
        rotated_at,
        created_at,
        updated_at
      from user_session
      where id = $1
    `,
    [sessionId],
  );
  const row = result.rows[0];

  if (!row) {
    return null;
  }

  return {
    id: String(row.id),
    userId: String(row.user_id),
    refreshTokenHash: String(row.refresh_token_hash),
    refreshExpiresAt: Date.parse(String(row.refresh_expires_at)),
    revokedAt: row.revoked_at ? Date.parse(String(row.revoked_at)) : null,
    rotatedAt: row.rotated_at ? Date.parse(String(row.rotated_at)) : null,
    createdAt: Date.parse(String(row.created_at)),
    updatedAt: Date.parse(String(row.updated_at)),
  };
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

async function ensureDatabaseUser(
  db: DbClient,
  email: string,
): Promise<string> {
  const existing = await db.query(
    `select id from user_account where email = $1`,
    [email],
  );
  const existingId = existing.rows[0]?.id;
  if (typeof existingId === "string" && existingId.length > 0) {
    return existingId;
  }

  const userId =
    email === SHARED_TEST_ACCOUNT.email
      ? SHARED_TEST_ACCOUNT.userId
      : crypto.randomUUID();

  await db.query(
    `
      insert into user_account(id, email, created_at)
      values ($1, $2, $3)
      on conflict (email) do nothing
    `,
    [userId, email, iso(Date.now())],
  );

  return userId;
}

function iso(epochMillis: number | null): string | null {
  return epochMillis === null ? null : new Date(epochMillis).toISOString();
}

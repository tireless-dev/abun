import { HttpError } from "../http/errors";
import type { DbClient } from "../db/transaction";
import { withTransaction } from "../db/transaction";

const SHARED_TEST_ACCOUNT = {
  email: "abun@tireless.dev",
  otp: "424242",
  userId: "abun",
} as const;

interface PendingOtp {
  code: string;
  expiresAt: number;
}

export interface OtpVerifyResult {
  accessToken: string;
  userId: string;
}

export interface AuthServiceLike {
  requestOtp(email: string): Promise<void> | void;
  verifyOtp(email: string, otp: string): Promise<OtpVerifyResult> | OtpVerifyResult;
}

export class AuthService implements AuthServiceLike {
  private readonly otpCodes = new Map<string, PendingOtp>();
  private readonly userIdsByEmail = new Map<string, string>();

  requestOtp(email: string): void {
    const normalized = normalizeEmail(email);
    const code =
      normalized === SHARED_TEST_ACCOUNT.email
        ? SHARED_TEST_ACCOUNT.otp
        : generateOtp();

    this.otpCodes.set(normalized, {
      code,
      expiresAt: Date.now() + 10 * 60 * 1000,
    });
  }

  verifyOtp(email: string, otp: string): OtpVerifyResult {
    const normalized = normalizeEmail(email);
    const pending = this.otpCodes.get(normalized);

    if (!pending) {
      throw new HttpError(400, "otp not requested");
    }

    if (pending.code !== otp.trim()) {
      throw new HttpError(400, "invalid otp");
    }

    if (pending.expiresAt <= Date.now()) {
      throw new HttpError(400, "otp expired");
    }

    this.otpCodes.delete(normalized);

    const userId = this.ensureUser(normalized);
    return {
      accessToken: `uid:${userId}`,
      userId,
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
  constructor(private readonly db: DbClient) {}

  async requestOtp(email: string): Promise<void> {
    const normalized = normalizeEmail(email);
    const code =
      normalized === SHARED_TEST_ACCOUNT.email
        ? SHARED_TEST_ACCOUNT.otp
        : generateOtp();
    const now = new Date();

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

  async verifyOtp(email: string, otp: string): Promise<OtpVerifyResult> {
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

      if (Date.parse(String(pending.expires_at)) <= Date.now()) {
        throw new HttpError(400, "otp expired");
      }

      const userId = await ensureDatabaseUser(tx, normalized);
      await tx.query(`delete from otp_code where email = $1`, [normalized]);

      return {
        accessToken: `uid:${userId}`,
        userId,
      };
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

  if (dbClient) {
    return new DatabaseAuthService(dbClient);
  }

  defaultAuthService ??= new AuthService();
  return defaultAuthService;
}

function isAuthServiceLike(value: unknown): value is AuthServiceLike {
  return (
    typeof value === "object" &&
    value !== null &&
    "requestOtp" in value &&
    typeof value.requestOtp === "function" &&
    "verifyOtp" in value &&
    typeof value.verifyOtp === "function"
  );
}

function normalizeEmail(email: string): string {
  const normalized = email.trim().toLowerCase();

  if (normalized.length === 0) {
    throw new HttpError(400, "email is required");
  }

  return normalized;
}

function generateOtp(): string {
  return `${Math.floor(100000 + Math.random() * 900000)}`;
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
    [userId, email, new Date().toISOString()],
  );

  return userId;
}

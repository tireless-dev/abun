import type { WorkerEnv } from "../env";
import { HttpError } from "./errors";

export function isAuthRequired(env: Partial<WorkerEnv>): boolean {
  return env.ABUN_REQUIRE_AUTH?.toLowerCase() === "true";
}

export function getBearerToken(request: Request): string | null {
  const authorization = request.headers.get("authorization");

  if (typeof authorization !== "string") {
    return null;
  }

  const match = authorization.match(/^Bearer\s+(.+)$/i);
  const token = match?.[1]?.trim();

  return token && token.length > 0 ? token : null;
}

export function requireBearerToken(
  request: Request,
  env: Partial<WorkerEnv>,
): string | null {
  if (!isAuthRequired(env)) {
    return null;
  }

  const token = getBearerToken(request);

  if (token === null) {
    throw new HttpError(401, "Missing or invalid bearer token");
  }

  return token;
}

export function resolveUserId(
  request: Request,
  env: Partial<WorkerEnv>,
): string {
  const token = getBearerToken(request);

  if (token !== null) {
    return token.startsWith("uid:") ? token.slice(4) : token;
  }

  if (isAuthRequired(env)) {
    throw new HttpError(401, "Missing or invalid bearer token");
  }

  return request.headers.get("x-user-id") ?? "demo-user";
}

import type { WorkerEnv } from "../env";
import type { AuthServiceLike } from "../services/auth-service";
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

export async function resolveUserId(
  request: Request,
  env: Partial<WorkerEnv>,
  authService: AuthServiceLike,
): Promise<string> {
  const token = getBearerToken(request);

  if (token !== null) {
    const session = await authService.authenticateAccessToken(token);
    return session.userId;
  }

  if (isAuthRequired(env)) {
    throw new HttpError(401, "Missing or invalid bearer token");
  }

  return request.headers.get("x-user-id") ?? "demo-user";
}

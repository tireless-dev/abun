export interface WorkerEnv {
  DB_URL: string;
  ABUN_REQUIRE_AUTH?: string;
}

export function requireEnvValue(
  env: Partial<WorkerEnv>,
  key: keyof WorkerEnv,
): string {
  const value = env[key];

  if (typeof value === "string" && value.length > 0) {
    return value;
  }

  throw new Error(`Missing required environment variable: ${key}`);
}

export function getDbUrl(env: Partial<WorkerEnv>): string {
  return requireEnvValue(env, "DB_URL");
}

export function hasDbUrl(env: Partial<WorkerEnv>): boolean {
  return typeof env.DB_URL === "string" && env.DB_URL.length > 0;
}

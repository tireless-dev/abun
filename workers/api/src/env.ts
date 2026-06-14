export interface HyperdriveBinding {
  connectionString: string;
}

export interface AssetsBinding {
  fetch(request: Request): Promise<Response>;
}

export interface WorkerEnv {
  DB_URL?: string;
  HYPERDRIVE?: HyperdriveBinding;
  ABUN_REQUIRE_AUTH?: string;
  ASSETS?: AssetsBinding;
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
  const hyperdriveConnectionString = env.HYPERDRIVE?.connectionString;
  if (typeof hyperdriveConnectionString === "string" && hyperdriveConnectionString.length > 0) {
    return hyperdriveConnectionString;
  }
  if (typeof env.DB_URL === "string" && env.DB_URL.length > 0) {
    return env.DB_URL;
  }
  return requireEnvValue(env, "DB_URL" as keyof WorkerEnv);
}

export function hasDbUrl(env: Partial<WorkerEnv>): boolean {
  return (
    (typeof env.HYPERDRIVE?.connectionString === "string" && env.HYPERDRIVE.connectionString.length > 0) ||
    (typeof env.DB_URL === "string" && env.DB_URL.length > 0)
  );
}
